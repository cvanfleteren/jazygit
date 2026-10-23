package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.SettingsProblem;

/**
 * What the user is told went wrong, as data; the view turns it into text.
 */
public sealed interface AppError {
    /**
     * Why {@code branch} has no upstream: none was set, or it was deleted on the remote.
     */
    static AppError noUpstream(Branch branch) {
        return branch.goneUpstream()
                .<AppError>map(upstream -> new UpstreamGone(branch.name(), upstream.shortName()))
                .getOrElse(() -> new NoUpstream(branch.name()));
    }

    /**
     * An operation on the repository failed.
     *
     * @param subject what it was done to, e.g. the branch that was pushed, if the operation names one
     */
    record OperationFailed(Operation operation, Option<String> subject, Failure failure) implements AppError {}

    /**
     * GitHub did not merge the pull request of {@code branch}, which it was asked to do in the background.
     *
     * @param message why, as GitHub says it
     */
    record MergeFailed(Branch.Name branch, String message) implements AppError {}

    /**
     * A commit was asked for, but nothing has changed.
     */
    record NothingToCommit() implements AppError {}

    /**
     * Amending was asked for, but nothing has changed to amend the commit with.
     */
    record NothingToAmend() implements AppError {}

    /**
     * Amending a merge commit was asked for, which is not possible.
     */
    record MergeNotAmendable() implements AppError {}

    /**
     * A stash was asked for, but nothing has changed.
     */
    record NothingToStash() implements AppError {}

    /**
     * The copied commit was asked to be cherry-picked, but the checked out branch already has it.
     */
    record AlreadyPicked(Sha sha) implements AppError {}

    /**
     * Continuing what the repository is in the middle of was asked for, but {@code files} files still have
     * conflicts.
     */
    record ConflictsRemain(int files) implements AppError {}

    /**
     * An operation was asked for that waits until the repository is in the middle of nothing, see
     * {@link Operation#waitsForUnfinished()}, but it is in the middle of {@code inProgress}, or {@code conflicts}
     * files still have conflicts.
     *
     * @param subject what it was to be done to, as for {@link OperationFailed}
     */
    record Unfinished(Operation operation, Option<String> subject, Option<InProgress> inProgress, int conflicts)
            implements AppError {}

    /**
     * The upstream entered for a push is not {@code <remote> <branch>}.
     */
    record InvalidUpstream(String text) implements AppError {}

    /**
     * A pull, or a reset onto the upstream, was asked for, but the branch has no upstream.
     */
    record NoUpstream(Branch.Name branch) implements AppError {}

    /**
     * A pull, or a reset onto the upstream, was asked for, but the upstream of the branch was deleted on the
     * remote.
     *
     * @param upstream the short name the upstream had, e.g. {@code origin/feature}
     */
    record UpstreamGone(Branch.Name branch, String upstream) implements AppError {}

    /**
     * Comparing a branch with the main branch was asked for, but there is no main branch to compare it with:
     * none is known, or it is gone.
     */
    record NoBaseBranch(Branch.Name branch) implements AppError {}

    /**
     * Comparing a branch with the main branch was asked for, but it is the main branch.
     */
    record IsBaseBranch(Branch.Name branch) implements AppError {}

    /**
     * An operation on the checked out branch was asked for, but HEAD is detached.
     */
    record NoBranchCheckedOut() implements AppError {}

    /**
     * A pull of a branch that is not checked out was asked for, but its commits not yet pushed keep it from
     * being fast-forwarded.
     */
    record UnpushedCommits(Branch.Name branch) implements AppError {}

    /**
     * An operation was asked for on a branch that is checked out in another worktree, which keeps git from
     * doing it here.
     *
     * @param worktree the root of the worktree the branch is checked out in
     */
    record CheckedOutElsewhere(Operation operation, Branch.Name branch, String worktree) implements AppError {}

    /**
     * The main worktree was asked to be removed, which holds the repository.
     *
     * @param worktree the name of its directory
     */
    record MainWorktreeNotRemovable(String worktree) implements AppError {}

    /**
     * The worktree shown was asked to be removed.
     *
     * @param worktree the name of its directory
     */
    record ShownWorktreeNotRemovable(String worktree) implements AppError {}

    /**
     * A locked worktree was asked to be removed, which its lock keeps git from doing.
     *
     * @param worktree the name of its directory
     * @param reason   why it was locked; empty when no reason was given
     */
    record WorktreeLocked(String worktree, String reason) implements AppError {}

    /**
     * The worktree shown was asked to be switched to.
     *
     * @param worktree the name of its directory
     */
    record WorktreeShownAlready(String worktree) implements AppError {}

    /**
     * A worktree whose directory is gone was asked to be switched to.
     *
     * @param worktree the name of its directory
     */
    record WorktreeMissing(String worktree) implements AppError {}

    /**
     * Pruning the worktrees was asked for, but none has its directory gone.
     */
    record NothingToPrune() implements AppError {}

    /**
     * The main worktree was asked to be locked or unlocked, which git refuses.
     */
    record MainWorktreeNotLockable() implements AppError {}

    /**
     * A pull request was asked for, but the branch has diverged from {@code upstream}, which only a force push
     * gets its commits onto.
     */
    record Diverged(Branch.Name branch, RemoteBranch upstream) implements AppError {}

    /**
     * A pull request was asked for, but no other branch is on the remote for it to merge into.
     */
    record NoBase(Branch.Name branch) implements AppError {}

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, cannot be checked out, as it is of
     * another repository.
     */
    record OtherRepository(String pullRequest) implements AppError {}

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, is not checked out while the working tree
     * has changes, which would come along.
     */
    record ChangesBeforeCheckout(String pullRequest) implements AppError {}

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, gets no worktree of its own, as its branch
     * is checked out here, which git checks out in one worktree at a time.
     */
    record PullRequestCheckedOutHere(String pullRequest) implements AppError {}

    /**
     * Commits are not copied onto {@code target}, a branch, while the working tree has changes, which git would
     * refuse to cherry-pick onto.
     */
    record ChangesBeforeMove(String target) implements AppError {}

    /**
     * Deleting a remote branch was asked for, but it is the default branch of its remote.
     *
     * @param branch the short name of the remote branch, e.g. {@code origin/main}
     */
    record DefaultBranch(String branch) implements AppError {}

    /**
     * Opening a branch on GitHub was asked for, but the branch is on no remote, or its upstream is gone.
     */
    record NotOnRemote(Branch.Name branch) implements AppError {}

    /**
     * A pull request was asked for before the pull requests were read, so it is unknown whether the branch has
     * one yet.
     */
    record PullRequestsNotRead() implements AppError {}

    /**
     * A pull request was asked for, but the pull requests could not be read, so it is unknown whether the branch
     * has one.
     */
    record PullRequestsUnavailable(Failure failure) implements AppError {}

    /**
     * The settings could not be read, or not all of them; the defaults are used instead.
     */
    record SettingsNotLoaded(SettingsProblem problem) implements AppError {}

    /**
     * A changed setting could not be kept for the next session; it still holds in this one.
     */
    record SettingsNotSaved(Failure failure) implements AppError {}

    /**
     * A recent repository was chosen, but could not be opened; it is not offered anymore.
     *
     * @param directory where it was
     */
    record RepositoryNotOpened(String directory, Failure failure) implements AppError {}

    /**
     * The gh executable chosen does not run, so it is not used.
     *
     * @param executable the one chosen; none for the one on the PATH
     */
    record GhExecutableUnusable(Option<String> executable, Failure failure) implements AppError {}

    /**
     * The account chosen for the repository could not be configured.
     */
    record GitHubAccountNotSet(Failure failure) implements AppError {}

    /**
     * The repository could not be shown again with the settings changed; they hold from the next start.
     */
    record SettingsNotApplied(Failure failure) implements AppError {}
}
