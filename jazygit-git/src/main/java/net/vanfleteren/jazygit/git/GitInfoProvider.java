package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
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

    /**
     * Creating and rewriting commits, moving them to another branch, and moving the checked out branch.
     */
    Commits commits();

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
