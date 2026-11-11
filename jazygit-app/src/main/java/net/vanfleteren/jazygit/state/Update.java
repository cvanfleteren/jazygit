package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next. The transitions of each feature live in their own class, next to their
 * {@link Msg} group; what many of them share, in {@link RereadUpdate} and {@link OperationUpdate}.
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
        return RereadUpdate.refresh(initial.mapRepoView(landing::applyTo)
                .mapJobs(jobs -> jobs.withOnArrival(landing.then()))
                .mapPreferences(preferences -> preferences
                        .withSettings(settings.settings())
                        .withThemeOverride(themeOverride)
                        .withRepositoryTheme(repositoryTheme))
                .mapOutcome(outcome -> outcome.withError(settings.problem().map(AppError.SettingsNotLoaded::new))));
    }

    /**
     * The start of a session as {@link #init(Model, SettingsStore.Loaded, Option, RepositoryTheme, Landing)} starts
     * it, at {@code now}: the commands it asks for are stamped (ADR 33).
     */
    public static Step begin(
            String repositoryName,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            RepositoryTheme repositoryTheme,
            Landing landing,
            Instant now) {
        return stamped(
                init(Model.initial(repositoryName).withNow(now), settings, themeOverride, repositoryTheme, landing),
                now);
    }

    public static Next update(Model model, Msg msg) {
        return arrived(handle(model, msg));
    }

    private static Next handle(Model model, Msg msg) {
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
                .map(msg -> {
                    Next handled = update(model.mapJobs(jobs -> jobs.withOnArrival(Option.none())), msg);
                    return new Next(
                            handled.model(), next.cmds().appendAll(handled.cmds()), next.quit() || handled.quit());
                })
                .getOrElse(next);
    }

    /**
     * What {@code msg}, handled at {@code now}, makes of {@code model}, as {@link #update(Model, Msg)}: the model is
     * told the time first, and the commands it asks for are stamped (ADR 33).
     */
    public static Step step(Model model, Msg msg, Instant now) {
        return stamped(update(model.withNow(now), msg), now);
    }

    /**
     * {@code next}, with each of its commands given the next id and running from {@code now} on.
     */
    private static Step stamped(Next next, Instant now) {
        long first = next.model().jobs().nextId();
        return new Step(
                next.model().mapJobs(jobs -> jobs.withStarted(next.cmds(), now)),
                next.cmds().zipWithIndex().map(cmd -> new Launch(first + cmd._2(), cmd._1())),
                next.quit());
    }

    /**
     * The next model, and the commands to run for it. A command is asked for once: one equal to one asked for before
     * it is dropped, as the model only knows what runs once the message is handled (ADR 39).
     *
     * @param quit whether the app stops once this is applied
     */
    public record Next(Model model, List<Cmd> cmds, boolean quit) {

        public Next {
            cmds = cmds.distinct();
        }

        public Next(Model model, List<Cmd> cmds) {
            this(model, cmds, false);
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
