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
     * The name of the repository, as {@link RepositoryIdentity#label()} has it: followed by the worktree in
     * parentheses when this is a linked one.
     */
    String repositoryName();

    /**
     * The root of the working tree of the repository.
     */
    Path worktree();

    /**
     * What git status says about the working tree and the index, and the stamps that tell whether to read more.
     */
    Status status();

    /**
     * Listing, renaming and deleting branches, as git branch does, and the branch each was made from.
     */
    Branches branches();

    // Deleting branches on their remote, with the credentials.

    /**
     * Deletes the branch {@code name}, locally and/or {@code remote}, the branch it is pushed to. With both, the
     * remote one goes first, so a failure leaves the local branch in place. Locally, git refuses a branch that is
     * not merged, which is a {@link GitError.NotFullyMerged}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> deleteBranch(
            BranchName name, RemoteBranch remote, DeleteScope scope);

    /**
     * Deletes {@code remote} on its remote.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote);

    /**
     * Checking out branches, as git checkout does, and finding the commit a revision the user typed names.
     */
    Checkout checkout();

    // Merging branches, as git merge does.

    /**
     * Merges {@code branch} into the checked out branch, as {@code mode} says. A merge that stops on conflicts is
     * left so, for the user to resolve them, and fails with {@link GitError.StoppedOnConflicts}; a squash merge is
     * then not committed.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode);

    // Copying commits onto the checked out branch, as git cherry-pick does.

    /**
     * Commits the changes of the commit {@code sha} on top of the checked out branch. A cherry-pick that stops on
     * conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}; one that stops because it changes
     * nothing is undone again, so the repository is left as it was.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha);

    // Undoing a commit with a new one, as git revert does.

    /**
     * Commits the inverse of {@code commit} on top of the checked out branch, with the message git writes; a merge
     * is reverted against its first parent. A revert that stops on conflicts is left so, and fails with
     * {@link GitError.StoppedOnConflicts}; one that fails otherwise is undone again, so the repository is left as it
     * was.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> revert(Commit commit);

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
    Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(BranchName onto);

    // Pushing branches, as git push does.

    /**
     * Pushes the local branch {@code branch}, which tracks {@code upstream}, where {@code push.default} says: to
     * {@code upstream}, or to the branch of the same name on its remote.
     *
     * @return the command lines that were run, or why git failed; {@link GitError.PushRefused} when
     *     {@code push.default} does not push the branch there, before anything is pushed
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> push(BranchName branch, RemoteBranch upstream, PushMode mode);

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, which becomes its upstream once the push
     * succeeded.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(
            BranchName branch, RemoteBranch upstream);

    // Pulling the checked out branch, as git pull does.

    /**
     * Pulls the checked out branch from its upstream, as git's {@code pull.rebase} and {@code pull.ff} say, without
     * asking for a merge message. A pull that stops on conflicts is left so, and fails with
     * {@link GitError.StoppedOnConflicts}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> pull();

    // Fetching from the remotes, as git fetch does.

    /**
     * Fetches from {@code remote}, and tells whether that changed a remote-tracking branch: one moved, came or went.
     * It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure<ProcessError>, Boolean> fetch(RemoteName remote);

    /**
     * Fast-forwards the local branch {@code branch}, which is not checked out, to {@code upstream}, the branch it
     * tracks. Fails when that is not a fast-forward.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream);

    // The remotes, as git remote lists them.

    /**
     * The remotes that fetching all fetches from: all of them but those with {@code skipFetchAll} set.
     */
    Either<ProcessError, List<RemoteName>> remotes();

    /**
     * The URL of each remote, by its name.
     */
    Either<ProcessError, Map<String, String>> remoteUrls();

    /**
     * The configuration of the repository itself, as git config --local reads and writes it.
     */
    Config config();

    /**
     * The history, as git log shows it.
     */
    History history();

    /**
     * Reading what changed: a commit, a pull request, a branch against its base, and the working tree and the index.
     */
    Diffs diffs();

    // What a pull request on GitHub changes, as GitHub shows it (ADR 55).

    /**
     * The files the pull request of {@code commits} changes: its head against the merge base of its head and its
     * base. Its commits are fetched first, from the remote of its repository, when the repository lacks either.
     */
    Either<ProcessError, List<CommitFile>> pullRequestFiles(PullRequestCommits commits);

    /**
     * Listing and changing stashes, as git stash does.
     */
    Stashes stashes();

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
     * Amends the commit {@code sha} with what is staged, keeping its message. Other than for the last commit, this
     * rebases the commits after it, unstaged changes being stashed meanwhile; the rebase is left as it stopped on
     * conflicts, see {@link #rebase}, and aborted when it fails otherwise, leaving the staged changes staged.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> amendCommit(Sha sha);

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

    // Moving commits to a new branch, as git checkout, cherry-pick and branch do.

    /**
     * Moves the commits of the checked out branch {@code from}, from {@code oldest} up to its tip, to the new
     * branch {@code to}, which is then checked out, and moves {@code from} back to the parent of {@code oldest}.
     * Without {@code onto}, the new branch starts where {@code from} is, so nothing is copied and the working tree
     * is left alone. With it, the new branch starts at {@code onto}, and the commits are cherry-picked onto it;
     * a cherry-pick that stops on conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}, and
     * {@code from} is only moved back once they are all copied. The new branch records the branch it starts at as
     * the one it came from, as {@link Branches#parent} reads it.
     *
     * @param oldest the full object id of a commit of {@code from} that has a parent
     * @param onto   the branch the new branch starts at, if not at {@code from}
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommits(
            BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto);

    /**
     * Moves the commits of the checked out branch {@code from}, from {@code oldest} up to its tip, onto the existing
     * branch {@code to}, which is then checked out, and moves {@code from} back to the parent of {@code oldest}. A
     * checkout that uncommitted changes are in the way of fails with {@link GitError.LocalChanges}. A cherry-pick that stops on conflicts is left
     * so, and fails with {@link GitError.StoppedOnConflicts}, and {@code from} is only moved back once the commits
     * are all copied.
     *
     * @param oldest the full object id of a commit of {@code from} that has a parent
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> moveCommitsTo(BranchName from, Sha oldest, BranchName to);

    // Moving the checked out branch, as git reset does.

    /**
     * Moves the checked out branch to {@code target}, doing with the changes {@code mode} says.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode);

    /**
     * Operations on the index.
     */
    Index index();

    /**
     * Listing, adding and removing the worktrees of the repository, as git worktree does.
     */
    Worktrees worktrees();

    /**
     * Editing files, as git edits commit messages.
     */
    Editor editor();
}
