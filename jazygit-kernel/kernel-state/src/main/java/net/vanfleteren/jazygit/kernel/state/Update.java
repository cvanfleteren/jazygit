package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.Cmd.*;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.kernel.state.preferences.SettingsError;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next. The transitions of each feature live in their own class, next to their
 * {@link Msg} group.
 */
public final class Update {

    private Update() {}

    /**
     * The start of a session from {@code initial} with {@code settings}, drawn in {@code themeOverride} instead of the
     * saved theme if there is one, in a repository the git configuration gives {@code repositoryTheme} (ADR 70), and
     * shown where {@code landing} says. What was wrong with the settings shows in the command log.
     */
    public static Next init(
            Model initial,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            RepositoryTheme repositoryTheme,
            Landing landing) {
        // Commits are loaded once the first status tells us where HEAD is.
        Next read = RereadUpdate.refresh(initial.mapRepoView(landing::applyTo)
                .mapJobs(jobs -> jobs.withOnArrival(landing.then()))
                .mapPreferences(preferences -> preferences
                        .withSettings(settings.settings())
                        .withFile(settings)
                        .withReported(settings.problem())
                        .withThemeOverride(themeOverride)
                        .withRepositoryTheme(repositoryTheme))
                .mapOutcome(outcome -> outcome.withError(settings.problem().map(SettingsError.NotLoaded::new))));
        return Events.happened(read, new KernelEvent.RepositoryOpened());
    }

    /**
     * The start of a session as {@link #init(Model, SettingsStore.Loaded, Option, RepositoryTheme, Landing)} starts
     * it, at {@code now}: the commands it asks for are stamped (ADR 33). The features answer what happens in the
     * kernel with {@code reactions}, from the opening of the repository on (ADR 166).
     */
    public static Step begin(
            String repositoryName,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            RepositoryTheme repositoryTheme,
            Landing landing,
            Reactions reactions,
            Instant now) {
        return stamped(
                init(
                        Model.initial(repositoryName).withNow(now).withReactions(reactions),
                        settings,
                        themeOverride,
                        repositoryTheme,
                        landing),
                now);
    }

    /**
     * What {@code msg} makes of {@code model}. A command is asked for once: one equal to one asked for before it is
     * dropped, once here, as the model only knows what runs once the message is handled (ADR 39).
     */
    public static Next update(Model model, Msg msg) {
        return once(handled(model, msg));
    }

    /**
     * {@code next}, with each command in it once.
     */
    public static Next once(Next next) {
        return new Next(next.model(), next.cmds().distinct(), next.quit());
    }

    private static Next handled(Model model, Msg msg) {
        return arrived(handle(model, msg));
    }

    private static Next handle(Model model, Msg msg) {
        if (msg instanceof Msg.Periodic periodic
                && !periodic.on(model.preferences().settings())) {
            return model.jobs().away() && periodic.readOnReturn()
                    ? Next.of(model.mapJobs(jobs -> jobs.keptWhileAway(periodic, model.now())))
                    : Next.of(model);
        }
        if (model.jobs().away() && msg instanceof Msg.Periodic periodic) {
            return model.jobs().dueWhileAway(periodic, model.now())
                    ? msg.apply(model.mapJobs(jobs -> jobs.handledWhileAway(periodic, model.now())))
                    : Next.of(model.mapJobs(jobs -> jobs.keptWhileAway(periodic, model.now())));
        }
        return msg.apply(model);
    }

    /**
     * {@code next}, and what the repository was opened to do once its status, branches and worktrees are read, if it
     * was and they are now: that is handled once (ADR 68). The status tells what changes a checkout would take along.
     */
    private static Next arrived(Next next) {
        Model model = next.model();
        return model.jobs()
                .onArrival()
                .filter(msg -> model.repo().status() instanceof Loadable.Loaded
                        && model.repo().branches() instanceof Loadable.Loaded
                        && model.repo().worktrees() instanceof Loadable.Loaded)
                .map(msg -> next.then(m -> handled(m.mapJobs(jobs -> jobs.withOnArrival(Option.none())), msg)))
                .getOrElse(next);
    }

    /**
     * The terminal got the focus, or lost it. While away, the timers read less: each of their messages is handled
     * once every {@link Msg.Periodic#whileAway()} (ADR 67); once back, what they asked for since it was last handled
     * is read once, as each of their messages is handled once (ADR 58), unless the user turned its timer off since,
     * and it is not one that is read once back even then (ADR 125).
     */
    static Next focused(Model model, boolean focused) {
        if (!focused) {
            return Next.of(model.mapJobs(
                    jobs -> jobs.away() ? jobs : jobs.withAway(true).withAwayAt(HashMap.empty())));
        }
        Set<Msg> missed = model.jobs().missed();
        return missed.foldLeft(
                Next.of(model.mapJobs(jobs ->
                        jobs.withAway(false).withMissed(LinkedHashSet.empty()).withAwayAt(HashMap.empty()))),
                (next, msg) -> next.then(m -> replayed(m, msg)));
    }

    /**
     * {@code msg}, kept while away, handled now that the terminal has the focus again: neither the away check nor the
     * switch of a read that is read once back applies to it (ADR 125).
     */
    private static Next replayed(Model model, Msg msg) {
        return msg instanceof Msg.Periodic periodic
                        && !periodic.on(model.preferences().settings())
                        && !periodic.readOnReturn()
                ? Next.of(model)
                : arrived(msg.apply(model));
    }

    /**
     * What {@code msg}, handled at {@code now}, makes of {@code model}, as {@link #update(Model, Msg)}: the model is
     * told the time first, and the commands it asks for are stamped (ADR 33).
     */
    public static Step step(Model model, Msg msg, Instant now) {
        return stamped(update(model.withNow(now), msg), now);
    }

    /**
     * {@code next}, with each of its commands given the next id and running from {@code now} on; a read of the open
     * pull requests also gets the id of a read of GitHub (ADR 119).
     */
    private static Step stamped(Next next, Instant now) {
        long first = next.model().jobs().nextId();
        List<Cmd> cmds = next.cmds().distinct();
        return new Step(
                next.model()
                        .mapJobs(jobs -> jobs.withStarted(cmds, now))
                        .mapPart(GitHubState.PART, gitHub -> gitHub.withStarted(cmds)),
                cmds.zipWithIndex().map(cmd -> new Launch(first + cmd._2(), cmd._1())),
                next.quit());
    }

    /**
     * The next model, and the commands to run for it. A command is asked for once: {@link #update(Model, Msg)} and the start
     * of a session drop one equal to one asked for before it, once per step, as the model only knows what runs once
     * the message is handled (ADR 39).
     *
     * @param quit whether the app stops once this is applied
     */
    public record Next(Model model, List<Cmd> cmds, boolean quit) {

        public Next(Model model, List<Cmd> cmds) {
            this(model, cmds, false);
        }

        /**
         * This, with {@code f} applied to its model: the commands and whether the app stops stay.
         */
        public Next map(UnaryOperator<Model> f) {
            return new Next(f.apply(model), cmds, quit);
        }

        /**
         * This, and {@code more} commands to run after its own.
         */
        public Next and(Cmd... more) {
            return new Next(model, cmds.appendAll(List.of(more)), quit);
        }

        /**
         * This, followed by what {@code f} makes of its model: the commands of both run, in that order, and the app
         * stops if either says so.
         */
        public Next then(Function<Model, Next> f) {
            Next next = f.apply(model);
            return new Next(next.model(), cmds.appendAll(next.cmds()), quit || next.quit());
        }

        /**
         * This, and the app stops.
         */
        public Next quitting() {
            return new Next(model, cmds, true);
        }

        public static Next of(Model model, Cmd... cmds) {
            return new Next(model, List.of(cmds));
        }
    }

    /**
     * A command to run, with the id that tells it apart from every other one, also from an equal one.
     */
    public record Launch(long id, Cmd cmd) {}

    /**
     * The next model, and the commands to run for it, each with its id: what {@link Program} applies.
     *
     * @param quit whether the app stops once this is applied
     */
    public record Step(Model model, List<Launch> launches, boolean quit) {}
}
