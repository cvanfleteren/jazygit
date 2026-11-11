package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd.*;

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
     * Starts {@code cmd}: its branch, if it has one, is busy until it is done ({@link Jobs#busy()}). One that waits
     * until the repository is in the middle of nothing is not started while it is, which the user is told instead.
     */
    public static Next start(Model model, OperationCmd cmd) {
        return unfinished(model, cmd).map(error -> Next.of(model.fail(error))).getOrElse(() -> Next.of(model, cmd));
    }

    private static Option<AppError> unfinished(Model model, OperationCmd cmd) {
        return Option.when(
                        cmd.operation().waitsForUnfinished(),
                        () -> model.repo().status().toOption())
                .flatMap(status -> status)
                .filter(status -> status.inProgress().isDefined() || status.conflicts() > 0)
                .map(status -> new AppError.Unfinished(
                        cmd.operation(), cmd.subject(), status.inProgress(), status.conflicts()));
    }

    /**
     * Opens {@code popup}, which asks about an operation to start: the error of the last one is not shown
     * anymore.
     */
    public static Next ask(Model model, Popup popup) {
        return Next.of(model.clearError().openPopup(popup));
    }

    /**
     * Opens {@code popup} as {@link #ask(Model, Popup)} does, if there is one: none when the operation it would
     * ask about cannot be done, and then nothing happens.
     */
    public static Next ask(Model model, Option<? extends Popup> popup) {
        return popup.map(open -> ask(model, open)).getOrElse(() -> Next.of(model));
    }

    /**
     * Confirms the open popup of type {@code type}: closes it, clears the error and starts the operation
     * {@code cmd} makes of it. When no such popup is open, or {@code cmd} makes none because what was entered
     * cannot be used, nothing happens.
     */
    public static <P extends Popup> Next confirm(
            Model model, Class<P> type, Function<? super P, Option<? extends OperationCmd>> cmd) {
        return model.popup(type)
                .flatMap(cmd)
                .map(operation -> start(model.withoutPopup(type).clearError(), operation))
                .getOrElse(() -> Next.of(model));
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
