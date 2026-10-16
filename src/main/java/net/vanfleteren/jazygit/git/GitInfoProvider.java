package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Everything the TUI does with git: the information it shows and the operations it performs, grouped by the
 * git subcommand they run. Each operation that changes the repository returns the command lines it ran, or why
 * it failed together with the command lines run up to then.
 */
public interface GitInfoProvider {

    /**
     * The name of the repository, i.e. the name of its working tree directory.
     */
    String repositoryName();

    /**
     * The root of the working tree of the repository.
     */
    Path workTree();

    // What git status says about the working tree and the index.
    Either<ProcessError, RepoStatus> status();

    // Listing, renaming and deleting branches, as git branch does.

    /**
     * The local branches: the checked out one first, then the most recently committed to.
     */
    Either<ProcessError, List<Branch>> branches();

    /**
     * The remote-tracking branches, the most recently committed to first.
     */
    Either<ProcessError, List<RemoteRef>> remoteBranches();

    /**
     * Deletes the branch {@code name}, locally and/or {@code remote}, the branch it is pushed to. With both, the
     * remote one goes first, so a failure leaves the local branch in place.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(
            Branch.Name name, RemoteBranch remote, DeleteScope scope);

    /**
     * Renames the local branch {@code name} to {@code newName}; it keeps following the same upstream. Fails when a
     * branch is called {@code newName} already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> renameBranch(Branch.Name name, Branch.Name newName);

    /**
     * Deletes {@code remote} on its remote.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote);

    // Checking out branches, as git checkout does.

    /**
     * Checks out the local branch {@code branch}. Fails with {@link GitError.LocalChanges} when
     * uncommitted changes would be overwritten.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(Branch.Name branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were; when popping them on {@code branch} fails, they stay in the stash, which is a
     * {@link GitError.StashNotRestored}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(Branch.Name branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(Branch.Name name, String startPoint);

    /**
     * Creates the local branch {@code name} at the remote-tracking branch of {@code upstream}, following it, and
     * checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(
            Branch.Name name, RemoteBranch upstream);

    // Merging branches, as git merge does.

    /**
     * Merges {@code branch} into the checked out branch, as {@code mode} says. A merge that stops on conflicts is
     * left so, for the user to resolve them, and fails with {@link GitError.StoppedOnConflicts}; a squash merge is
     * then not committed.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> merge(Branch.Name branch, MergeMode mode);

    // Copying commits onto the checked out branch, as git cherry-pick does.

    /**
     * Commits the changes of the commit {@code sha} on top of the checked out branch. A cherry-pick that stops on
     * conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}; one that stops because it changes
     * nothing is undone again, so the repository is left as it was.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha);

    // Going on with what the repository is in the middle of, as git merge, rebase, cherry-pick and revert do.

    /**
     * Does {@code action} to {@code inProgress}, the operation the repository stopped in the middle of. Continuing
     * takes the commit message git prepared, without asking; when it stops on the conflicts of a next commit, it
     * fails with {@link GitError.StoppedOnConflicts}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(InProgress inProgress, InProgressAction action);

    // Rebasing the checked out branch, as git rebase does.

    /**
     * Rebases the checked out branch onto {@code onto}, bringing uncommitted changes along. A rebase that stops on
     * conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}; when it fails otherwise, it is
     * aborted again, so the repository is left as it was.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(Branch.Name onto);

    // Pushing branches, as git push does.

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, the branch it tracks.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(
            Branch.Name branch, RemoteBranch upstream, PushMode mode);

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, which becomes its upstream once the push
     * succeeded.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            Branch.Name branch, RemoteBranch upstream);

    // Pulling the checked out branch, as git pull does.

    /**
     * Pulls the checked out branch from its upstream, as git's {@code pull.rebase} and {@code pull.ff} say, without
     * asking for a merge message. A pull that stops on conflicts is left so, and fails with
     * {@link GitError.StoppedOnConflicts}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> pull();

    // Fetching from the remotes, as git fetch does.

    /**
     * Fetches from {@code remote}. It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote);

    /**
     * Fast-forwards the local branch {@code branch}, which is not checked out, to {@code upstream}, the branch it
     * tracks. Fails when that is not a fast-forward.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(Branch.Name branch, RemoteBranch upstream);

    // The remotes, as git remote lists them.

    /**
     * The remotes that fetching all fetches from: all of them but those with {@code skipFetchAll} set.
     */
    Either<ProcessError, List<String>> remotes();

    /**
     * The URL of each remote, by its name.
     */
    Either<ProcessError, Map<String, String>> remoteUrls();

    // The configuration of the repository itself, as git config --local reads and writes it.

    /**
     * The value of {@code key} in the configuration of the repository, if it is set there.
     */
    Either<ProcessError, Option<String>> localConfig(String key);

    /**
     * Sets {@code key} to {@code value} in the configuration of the repository, or removes it there for none.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> setLocalConfig(String key, Option<String> value);

    // The history, as git log shows it.

    /**
     * The log of HEAD, newest first.
     */
    Either<ProcessError, List<Commit>> commits();

    /**
     * The log of the branch {@code branch}, local or remote-tracking, newest first.
     */
    Either<GitError, List<Commit>> log(BranchRef branch);

    // A single commit, as git show shows it.

    /**
     * What the commit {@code sha} changed, like {@code git show}: the changed files, a summary of the
     * changes, a blank line and the diff. Long output is cut off.
     */
    Either<ProcessError, DiffText> commitChanges(Sha sha);

    // The changes in the working tree and the index, as git diff shows them.

    /**
     * The staged and the unstaged diff of {@code files}.
     */
    Either<GitError, Diffs> diff(List<FileEntry> files);

    // Listing and changing stashes, as git stash does.

    /**
     * The stashes, newest first.
     */
    Either<ProcessError, List<Stash>> stashes();

    /**
     * Stashes the changes, staged and unstaged, and the untracked files, under the name {@code message}.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message);

    /**
     * Applies {@code stash} to the working tree and drops it. When that conflicts, the stash is kept.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Stash stash);

    /**
     * Deletes {@code stash} without applying it.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Stash stash);

    // Creating and rewriting commits, as git commit does, and through an interactive rebase for older commits.

    /**
     * Commits what is staged.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message);

    /**
     * Amends the last commit with what is staged, keeping its message.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLastCommit();

    /**
     * Replaces the message of the commit {@code sha}, leaving its content alone. Other than for the last commit,
     * this rebases the commits after it, uncommitted changes being stashed meanwhile; the rebase is left
     * as it stopped on conflicts, see {@link #rebase}, and aborted when it fails otherwise.
     *
     * @param sha the full object id of the commit
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message);

    /**
     * Folds the commit {@code sha} into its parent, keeping the messages {@code mode} says. This rebases the
     * commits after it, uncommitted changes being stashed meanwhile; the rebase is left as it stopped on
     * conflicts, see {@link #rebase}, and aborted when it fails otherwise.
     *
     * @param sha the full object id of a commit that has exactly one parent
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode);

    /**
     * Removes the commit {@code sha} from the history of the checked out branch. This rebases the commits after
     * it, uncommitted changes being stashed meanwhile; the rebase is left as it stopped on conflicts, see
     * {@link #rebase}, and aborted when it fails otherwise.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> dropCommit(Sha sha);

    // Moving the checked out branch, as git reset does.

    /**
     * Moves the checked out branch to {@code target}, doing with the changes {@code mode} says.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode);

    // Operations on the index.

    /**
     * Stages the changes of {@code paths}.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> stage(List<String> paths);

    /**
     * Removes the newly added {@code paths} from the index; they stay in the working tree, untracked.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstageNew(List<String> paths);

    /**
     * Throws changes away as described by {@code plan}. This cannot be undone.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> discard(DiscardPlan plan);

    /**
     * Resets the index entries of {@code paths} to HEAD.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> unstage(List<String> paths);

    // Listing, adding and removing the worktrees of the repository, as git worktree does.

    /**
     * The worktrees, the main one first; none for a bare repository itself, which has no working tree.
     */
    Either<ProcessError, List<Worktree>> worktrees();

    /**
     * Checks {@code branch} out in a new worktree at {@code location}, which is taken relative to the working
     * tree of this one. Git refuses a branch that is checked out already, and a location that is not empty.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktree(Path location, Branch.Name branch);

    /**
     * Removes the linked worktree at {@code location}, its directory included; its branch stays. Without
     * {@code force}, git refuses one with modified or untracked files, which is a
     * {@link GitError.WorktreeHasChanges}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force);
}
