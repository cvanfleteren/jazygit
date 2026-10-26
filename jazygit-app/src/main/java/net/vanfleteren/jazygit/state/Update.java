package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd.*;
import net.vanfleteren.jazygit.state.Loadable.Failed;

/**
 * The pure state transitions of the app: given a model and a message, the next model and the IO
 * to perform next. The transitions of each feature live in their own class, next to their
 * {@link Msg} group.
 */
public final class Update {

    /**
     * How long a tick waits after reading which worktrees have changes before reading that again: it takes a
     * git status per worktree, too many to run on every tick.
     */
    static final Duration WORKTREE_CHANGES_INTERVAL = Duration.ofSeconds(3);

    /**
     * Loads that reading the repository again repeats. They are skipped while a previous one is still running.
     */
    private static final List<Cmd.Load> PERIODIC =
            List.<Cmd.Load>of(new LoadStatus()).appendAll(CheckMetadata.GUARDED);

    /**
     * Loads that a tick repeats: the status, and those that {@link CheckMetadata} guards only once their files
     * changed.
     */
    private static final List<Cmd.Load> TICKED = List.of(new LoadStatus(), new CheckMetadata());

    private Update() {}

    public static Next init(String repositoryName) {
        return init(repositoryName, SettingsStore.Loaded.DEFAULT);
    }

    /**
     * The start of a session with {@code settings}; what was wrong with them shows in the command log.
     */
    public static Next init(String repositoryName, SettingsStore.Loaded settings) {
        return init(repositoryName, settings, Option.none());
    }

    /**
     * The start of a session with {@code settings}, drawn in {@code themeOverride} instead of the saved theme
     * if there is one.
     */
    public static Next init(String repositoryName, SettingsStore.Loaded settings, Option<ThemeName> themeOverride) {
        return init(repositoryName, settings, themeOverride, Landing.START);
    }

    /**
     * The start of a session as {@link #init(String, SettingsStore.Loaded, Option)} starts it, shown where
     * {@code landing} says.
     */
    public static Next init(
            String repositoryName, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, Landing landing) {
        return init(Model.initial(repositoryName), settings, themeOverride, landing);
    }

    private static Next init(
            Model initial, SettingsStore.Loaded settings, Option<ThemeName> themeOverride, Landing landing) {
        // Commits are loaded once the first status tells us where HEAD is.
        return refresh(initial.mapRepoView(landing::applyTo)
                .mapPreferences(preferences ->
                        preferences.withSettings(settings.settings()).withThemeOverride(themeOverride))
                .mapOutcome(outcome -> outcome.withError(settings.problem().map(AppError.SettingsNotLoaded::new))));
    }

    /**
     * The start of a session as {@link #init(String, SettingsStore.Loaded, Option, Landing)} starts it, at
     * {@code now}: the commands it asks for are stamped (ADR 33).
     */
    public static Step begin(
            String repositoryName,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            Landing landing,
            Instant now) {
        return stamped(init(Model.initial(repositoryName).withNow(now), settings, themeOverride, landing), now);
    }

    public static Next update(Model model, Msg msg) {
        return msg.apply(model);
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
     * Starts {@code cmd}: its branch, if it has one, is busy until it is done. One that waits until the repository
     * is in the middle of nothing is not started while it is, which the user is told instead.
     */
    public static Next start(Model model, OperationCmd cmd) {
        return unfinished(model, cmd)
                .map(error -> Next.of(model.fail(error)))
                .getOrElse(() -> Next.of(
                        cmd.busyBranch()
                                .map(branch -> model.withBusyBranch(branch, cmd.operation()))
                                .getOrElse(model),
                        cmd));
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
     * Reads the repository again: the periodic loads, and whatever failed to load before. A load that is still
     * running is not started again. What an operation changed is read at once this way, without waiting for the
     * files to tell.
     */
    public static Next refresh(Model model) {
        return reload(model, PERIODIC, load -> true);
    }

    /**
     * Reads the repository again on a tick, as {@link #refresh(Model)} does, but the branches, the stashes and the
     * worktrees only once the files they are read from changed: each takes a git process, and they hardly change
     * while the user does nothing. What failed to load is only tried again once it waited long enough
     * ({@link Jobs#retryDue}), so that a read that keeps failing, like the preview of a commit that is gone, does not
     * take a git process on every tick.
     */
    public static Next tick(Model model) {
        Model ticked = model.mapJobs(Jobs::ticked);
        return reload(ticked, TICKED, ticked.jobs()::retryDue);
    }

    /**
     * @param due whether to try a load that failed again now
     */
    private static Next reload(Model model, List<Cmd.Load> periodic, Predicate<Cmd.Load> due) {
        List<Cmd.Load> retries = retries(model);
        List<Cmd.Load> loads = periodic.appendAll(retries.filter(due))
                .filter(cmd -> !model.jobs().refreshing().contains(cmd))
                .appendAll(worktreeChanges(model)
                        .filter(load -> model.jobs()
                                .worktreeChangesRead()
                                .forAll(read -> !model.now().isBefore(read.plus(WORKTREE_CHANGES_INTERVAL)))));
        return new Next(
                model.mapJobs(jobs ->
                        jobs.withRefreshing(jobs.refreshing().addAll(loads)).retainingRetries(retries.toSet())),
                List.narrow(loads));
    }

    /**
     * The loads to run again as they failed, while their failure is still shown.
     */
    private static List<Cmd.Load> retries(Model model) {
        return List.<Cmd.Load>empty()
                .appendAll(Option.when(
                        model.repo().commits() instanceof Failed || model.jobs().commitsFailed(),
                        () -> new LoadCommits(AheadUpdate.ofHead(model))))
                .appendAll(AheadUpdate.retries(model))
                .appendAll(model.repoView()
                        .commitSearch()
                        .filter(search -> search.results() instanceof Failed)
                        .map(CommitSearch::load))
                .appendAll(model.repoView()
                        .branchLog()
                        .filter(log -> log.value() instanceof Failed)
                        .map(log -> new LoadBranchLog(log.key(), AheadUpdate.of(model, log.key()))))
                .appendAll(model.repoView()
                        .commitDetail()
                        .filter(detail -> detail.value() instanceof Failed)
                        .map(detail -> new LoadCommitDetail(
                                detail.key(), model.preferences().diffOptions(), Slot.COMMIT_DETAIL)))
                .appendAll(model.repoView()
                        .divergence()
                        .filter(view -> view.commits() instanceof Failed)
                        .map(DivergenceView::load))
                .appendAll(model.repoView()
                        .divergence()
                        .flatMap(DivergenceView::detail)
                        .filter(detail -> detail.value() instanceof Failed)
                        .map(detail -> new LoadCommitDetail(
                                detail.key(), model.preferences().diffOptions(), Slot.DIVERGENCE_DETAIL)))
                .appendAll(model.repoView()
                        .branchCommits()
                        .flatMap(BranchCommitsView::detail)
                        .filter(detail -> detail.value() instanceof Failed)
                        .map(detail -> new LoadCommitDetail(
                                detail.key(), model.preferences().diffOptions(), Slot.BRANCH_COMMITS_DETAIL)))
                .appendAll(model.repoView()
                        .stashDetail()
                        .filter(detail -> detail.value() instanceof Failed)
                        .map(detail -> new Cmd.LoadStashDetail(
                                detail.key(), model.preferences().diffOptions())));
    }

    /**
     * Reads which of the other worktrees have changes right away, however recently that was read, if the branches
     * pane shows the worktrees and that is not being read already.
     */
    public static Next readWorktreeChanges(Model model) {
        Option<Cmd.Load> load = worktreeChanges(model);
        return new Next(
                model.mapJobs(jobs -> jobs.withRefreshing(jobs.refreshing().addAll(load))), List.narrow(load.toList()));
    }

    /**
     * The load of which other worktrees have changes, while the branches pane shows the worktrees on screen: none
     * while it runs already, even for other worktrees, or when there are no others.
     */
    private static Option<Cmd.Load> worktreeChanges(Model model) {
        List<String> others = model.repo().otherWorktrees();
        return Option.<Cmd.Load>of(new LoadWorktreeChanges(others))
                .filter(load -> model.screen() == Screen.REPOSITORY
                        && model.repoView().branchesTab() == BranchesTab.WORKTREES
                        && others.nonEmpty()
                        && !model.jobs().refreshing().exists(running -> running instanceof LoadWorktreeChanges));
    }

    /**
     * Reads the highlighted diff again, as files can change on disk without their status changing. This waits
     * for the status: right after (un)staging, the entries the diff was read for are outdated, and reading
     * them again would show their changes as gone for as long as the new status takes to arrive.
     */
    static Next reloadDiff(Model model, List<Cmd> cmds) {
        return model.repoView()
                .fileDiff()
                .filter(d -> model.repo()
                        .status()
                        .toOption()
                        .exists(status -> status.files().containsAll(d.key())))
                .map(d -> new LoadFileDiff(d.key(), model.preferences().diffOptions()))
                .filter(cmd -> !model.jobs().refreshing().contains(cmd))
                .map(cmd -> new Next(
                        model.mapJobs(
                                jobs -> jobs.withRefreshing(jobs.refreshing().add(cmd))),
                        cmds.append(cmd)))
                .getOrElse(() -> new Next(model, cmds));
    }

    /**
     * What {@code then} makes of the changed files, once the status is loaded; until then, or when it failed to
     * load, nothing happens, as it is unknown what changed.
     */
    public static Next withFiles(Model model, Function<List<FileEntry>, Next> then) {
        return model.repo().status().toOption().map(RepoStatus::files).map(then).getOrElse(() -> Next.of(model));
    }

    public static List<FileEntry> files(Model model) {
        return model.repo().status().toOption().map(RepoStatus::files).getOrElse(List.empty());
    }

    /**
     * The next model, and the commands to run for it.
     *
     * @param quit whether the app stops once this is applied
     */
    public record Next(Model model, List<Cmd> cmds, boolean quit) {

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
