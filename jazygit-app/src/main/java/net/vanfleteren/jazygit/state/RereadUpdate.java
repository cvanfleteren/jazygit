package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.state.Cmd.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reading the repository again: after an operation, on a tick, and what failed to load before, while its failure is
 * still shown.
 */
public final class RereadUpdate {

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

    private RereadUpdate() {}

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
                .appendAll(repoView.commitSearch().map(CommitSearch::load))
                .appendAll(repoView.branchLog()
                        .map(log -> new LoadBranchLog(log.key(), AheadUpdate.of(model, log.key()))))
                .appendAll(repoView.commitDetail()
                        .map(detail -> new LoadCommitDetail(detail.key(), options, Slot.COMMIT_DETAIL)))
                .appendAll(repoView.divergence().map(DivergenceView::load))
                .appendAll(repoView.divergence()
                        .flatMap(DivergenceView::detail)
                        .map(detail -> new LoadCommitDetail(detail.key(), options, Slot.DIVERGENCE_DETAIL)))
                .appendAll(repoView.branchCommits()
                        .flatMap(BranchCommitsView::detail)
                        .map(detail -> new LoadCommitDetail(detail.key(), options, Slot.BRANCH_COMMITS_DETAIL)))
                .appendAll(repoView.stashDetail().map(detail -> new LoadStashDetail(detail.key(), options)))
                .appendAll(repoView.commitFiles().map(CommitFilesView::load))
                .appendAll(repoView.commitFiles()
                        .flatMap(view -> view.diff().map(diff -> view.diffLoad(diff.key(), options))));
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
}
