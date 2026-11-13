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
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd.*;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.load.CheckFileDiff;
import net.vanfleteren.jazygit.state.load.CheckMetadata;
import net.vanfleteren.jazygit.state.load.Load;
import net.vanfleteren.jazygit.state.load.LoadBranchLog;
import net.vanfleteren.jazygit.state.load.LoadBranches;
import net.vanfleteren.jazygit.state.load.LoadCommitDetail;
import net.vanfleteren.jazygit.state.load.LoadCommitFileDiff;
import net.vanfleteren.jazygit.state.load.LoadCommitFiles;
import net.vanfleteren.jazygit.state.load.LoadCommits;
import net.vanfleteren.jazygit.state.load.LoadDivergence;
import net.vanfleteren.jazygit.state.load.LoadFileDiff;
import net.vanfleteren.jazygit.state.load.LoadStashDetail;
import net.vanfleteren.jazygit.state.load.LoadStatus;
import net.vanfleteren.jazygit.state.load.LoadWorktreeChanges;
import net.vanfleteren.jazygit.state.load.SearchCommits;
import net.vanfleteren.jazygit.state.load.Targeted;
import net.vanfleteren.jazygit.state.operation.OperationCmd;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.preferences.SettingsError;
import net.vanfleteren.jazygit.state.view.BranchCommitsView;
import net.vanfleteren.jazygit.state.view.BranchesTab;
import net.vanfleteren.jazygit.state.view.DivergenceView;
import net.vanfleteren.jazygit.state.view.RepoView;

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
    private static final List<Load> PERIODIC = List.<Load>of(new LoadStatus()).appendAll(CheckMetadata.GUARDED);

    /**
     * Loads that a tick repeats: the status, and those that {@link CheckMetadata} guards only once their files
     * changed.
     */
    private static final List<Load> TICKED = List.of(new LoadStatus(), new CheckMetadata());

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
        return init(Model.initial(repositoryName), settings, themeOverride, RepositoryTheme.NONE, landing);
    }

    private static Next init(
            Model initial,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            RepositoryTheme repositoryTheme,
            Landing landing) {
        // Commits are loaded once the first status tells us where HEAD is.
        return refresh(initial.mapRepoView(landing::applyTo)
                .mapJobs(jobs -> jobs.withOnArrival(landing.then()))
                .mapPreferences(preferences -> preferences
                        .withSettings(settings.settings())
                        .withFile(settings)
                        .withReported(settings.problem())
                        .withThemeOverride(themeOverride)
                        .withRepositoryTheme(repositoryTheme))
                .mapOutcome(outcome -> outcome.withError(settings.problem().map(SettingsError.NotLoaded::new))));
    }

    /**
     * The start of a session as {@link #init(String, SettingsStore.Loaded, Option, Landing)} starts it, in a
     * repository the git configuration gives {@code repositoryTheme} (ADR 70), at {@code now}: the commands it asks
     * for are stamped (ADR 33).
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
     * is read once, as each of their messages is handled once (ADR 58).
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
                (next, msg) -> next.then(m -> handled(m, msg)));
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
                next.model().mapJobs(jobs -> jobs.withStarted(cmds, now)).mapGitHub(gitHub -> gitHub.withStarted(cmds)),
                cmds.zipWithIndex().map(cmd -> new Launch(first + cmd._2(), cmd._1())),
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
    private static Next reload(Model model, List<Load> periodic, Predicate<Load> due) {
        List<Load> retries = retries(model);
        List<Load> loads = periodic.appendAll(retries.filter(due))
                .map(load -> LoadBranches.current(model, load))
                .filter(cmd -> !model.jobs().runs(cmd))
                .appendAll(worktreeChanges(model)
                        .filter(load -> model.jobs()
                                .worktreeChangesRead()
                                .forAll(read -> !model.now().isBefore(read.plus(WORKTREE_CHANGES_INTERVAL)))));
        return new Next(model.mapJobs(jobs -> jobs.retainingRetries(retries.toSet())), List.narrow(loads));
    }

    /**
     * The loads to run again as they failed, while their failure is still shown.
     */
    private static List<Load> retries(Model model) {
        LoadCommits commits = new LoadCommits(AheadUpdate.ofHead(model));
        return Option.<Load>when(
                        commits.target().failedIn(model) || model.jobs().commitsFailed(), commits)
                .toList()
                .appendAll(AheadUpdate.retries(model))
                .appendAll(shown(model).filter(load -> load.target().failedIn(model)));
    }

    /**
     * The loads that read what the views that are open show, as the model would ask for them now. The diff of the
     * files highlighted is not one of them: every status reads it again when its files changed (ADR 58).
     */
    public static List<Targeted<?>> shown(Model model) {
        RepoView repoView = model.repoView();
        DiffOptions options = model.preferences().diffOptions();
        return List.<Targeted<?>>empty()
                .appendAll(repoView.commitSearch().map(SearchCommits::of))
                .appendAll(repoView.branchLog()
                        .map(log -> new LoadBranchLog(log.key(), AheadUpdate.of(model, log.key()))))
                .appendAll(repoView.commitDetail()
                        .map(detail -> new LoadCommitDetail(detail.key(), options, Slot.COMMIT_DETAIL)))
                .appendAll(repoView.divergence().map(LoadDivergence::of))
                .appendAll(repoView.divergence()
                        .flatMap(DivergenceView::detail)
                        .map(detail -> new LoadCommitDetail(detail.key(), options, Slot.DIVERGENCE_DETAIL)))
                .appendAll(repoView.branchCommits()
                        .flatMap(BranchCommitsView::detail)
                        .map(detail -> new LoadCommitDetail(detail.key(), options, Slot.BRANCH_COMMITS_DETAIL)))
                .appendAll(repoView.stashDetail().map(detail -> new LoadStashDetail(detail.key(), options)))
                .appendAll(repoView.commitFiles().map(LoadCommitFiles::of))
                .appendAll(repoView.commitFiles()
                        .flatMap(view -> view.diff().map(diff -> LoadCommitFileDiff.of(view, diff.key(), options))));
    }

    /**
     * Reads which of the other worktrees have changes right away, however recently that was read, if the branches
     * pane shows the worktrees and that is not being read already.
     */
    public static Next readWorktreeChanges(Model model) {
        return new Next(model, List.narrow(worktreeChanges(model).toList()));
    }

    /**
     * The load of which other worktrees have changes, while the branches pane shows the worktrees on screen: none
     * while it runs already, even for other worktrees, or when there are no others.
     */
    private static Option<Load> worktreeChanges(Model model) {
        List<String> others = model.repo().otherWorktrees();
        return Option.<Load>of(new LoadWorktreeChanges(others))
                .filter(load -> model.screen() == Screen.REPOSITORY
                        && model.repoView().branchesTab() == BranchesTab.WORKTREES
                        && others.nonEmpty()
                        && !model.jobs().runs(LoadWorktreeChanges.class::isInstance));
    }

    /**
     * Reads the highlighted diff again once its files changed, as they can change on disk without their status
     * changing: it looks at them first, without git (ADR 58), unless HEAD moved, which changes what the staged changes
     * are compared with. This waits for the status: right after (un)staging, the entries the diff was read for are
     * outdated, and reading them again would show their changes as gone for as long as the new status takes to
     * arrive.
     */
    public static Next reloadDiff(Model model, List<Cmd> cmds, boolean headMoved) {
        return shownDiff(model)
                .filter(load -> !model.jobs().runs(load) && !model.jobs().runs(new CheckFileDiff(load)))
                .map(load -> new Next(model, cmds.append(headMoved ? load : new CheckFileDiff(load))))
                .getOrElse(() -> new Next(model, cmds));
    }

    /**
     * The read of the highlighted diff, with the options now chosen, while the status still lists its files.
     */
    public static Option<LoadFileDiff> shownDiff(Model model) {
        return model.repoView()
                .fileDiff()
                .filter(d -> model.repo()
                        .status()
                        .toOption()
                        .exists(status -> status.files().containsAll(d.key())))
                .map(d -> new LoadFileDiff(d.key(), model.preferences().diffOptions()));
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
