package net.vanfleteren.jazygit.git;

import io.vavr.Tuple2;
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
     * How many commits a log or a search reads at most.
     */
    int MAX_COMMITS = 300;

    /**
     * The name of the repository, as {@link RepositoryIdentity#label()} has it: followed by the worktree in
     * parentheses when this is a linked one.
     */
    String repositoryName();

    /**
     * The root of the working tree of the repository.
     */
    Path worktree();

    // What git status says about the working tree and the index.
    Either<ProcessError, RepoStatus> status();

    /**
     * Stamps of the files of the git directory that the branches, the stashes and the worktrees are read from,
     * taken without running git; none when they cannot be looked at, and then those are to be read every time.
     */
    Option<GitMetadata> metadata();

    // Listing, renaming and deleting branches, as git branch does.

    /**
     * The local and the remote-tracking branches, read at the same moment.
     */
    Either<ProcessError, AllBranches> allBranches();

    /**
     * Deletes the branch {@code name}, locally and/or {@code remote}, the branch it is pushed to. With both, the
     * remote one goes first, so a failure leaves the local branch in place.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(
            BranchName name, RemoteBranch remote, DeleteScope scope);

    /**
     * Deletes the local branch {@code name}, merged or not.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name);

    /**
     * Renames the local branch {@code name} to {@code newName}; it keeps following the same upstream. Fails when a
     * branch is called {@code newName} already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> renameBranch(BranchName name, BranchName newName);

    /**
     * Deletes {@code remote} on its remote.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote);

    // Checking out branches, as git checkout does.

    /**
     * Checks out the local branch {@code branch}. Fails with {@link GitError.LocalChanges} when
     * uncommitted changes would be overwritten.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(BranchName branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were; when popping them on {@code branch} fails, they stay in the stash, which is a
     * {@link GitError.StashNotRestored}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(BranchName branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out; records {@code parent} as the
     * branch it came from, if given, as {@link #parentBranch} reads it.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(
            BranchName name, String startPoint, Option<BranchName> parent);

    /**
     * Creates the local branch {@code name} at the remote-tracking branch of {@code upstream}, following it, and
     * checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(
            BranchName name, RemoteBranch upstream);

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
     * Pushes the local branch {@code branch} to {@code upstream}, the branch it tracks.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(
            BranchName branch, RemoteBranch upstream, PushMode mode);

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
     * Fetches from {@code remote}. It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote);

    /**
     * Fast-forwards the local branch {@code branch}, which is not checked out, to {@code upstream}, the branch it
     * tracks. Fails when that is not a fast-forward.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream);

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

    /**
     * The branch {@code branch} was made from, if that was recorded: as {@code gh} does, in
     * {@code branch.<name>.gh-merge-base}.
     */
    Either<ProcessError, Option<BranchName>> parentBranch(BranchName branch);

    // The history, as git log shows it.

    /**
     * The log of HEAD, newest first.
     */
    Either<ProcessError, List<Commit>> commits();

    /**
     * The commits of the log of HEAD that {@code query} finds, newest first, as many as {@link #commits} reads at
     * most; none in a repository without commits.
     *
     * @param notOn the branch whose commits are left out, if any
     */
    Either<ProcessError, List<Commit>> search(CommitQuery query, Option<BranchRef> notOn);

    /**
     * The commits of {@code branch}, or of HEAD for none, that are not on the first of {@code bases} that exists:
     * at most as many as {@link #commits} reads, laid out as a log; and the commit they forked from. None when none
     * of {@code bases} exists, or HEAD has no commits yet.
     */
    Either<GitError, Option<Ahead>> ahead(Option<BranchRef> branch, List<BranchRef> bases);

    /**
     * The log of the branch {@code branch}, local or remote-tracking, newest first.
     */
    Either<GitError, List<Commit>> log(BranchRef branch);

    /**
     * The commits only on {@code branch} and the ones only on {@code other}, as {@code git log branch...other}
     * shows them.
     */
    Either<GitError, Divergence> divergence(BranchRef branch, BranchRef other);

    /**
     * The subject of the oldest commit on {@code branch} that is not on {@code base}; none when there is none.
     */
    Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base);

    /**
     * How many commits are on {@code branch} and not on {@code base}, counting no further than {@code max}.
     */
    Either<ProcessError, Integer> countNotOn(BranchRef branch, BranchRef base, int max);

    /**
     * The commits of the local branch {@code branch} from {@code oldest} up to its tip, and how many of them are
     * on {@code upstream}, the remote branch it follows, already.
     *
     * @param oldest the full object id of a commit of {@code branch} that has a parent
     */
    Either<GitError, CommitRange> commitRange(BranchName branch, Sha oldest, Option<RemoteBranch> upstream);

    // A single commit, as git show shows it.

    /**
     * What the commit {@code sha} changed, like {@code git show}: the changed files, a summary of the
     * changes, a blank line and the diff, read with {@code options}. Long output is cut off.
     */
    Either<ProcessError, DiffText> commitChanges(Sha sha, DiffOptions options);

    // The changes in the working tree and the index, as git diff shows them.

    /**
     * The staged and the unstaged diff of {@code files}, read with {@code options}.
     */
    Either<GitError, Diffs> diff(List<FileEntry> files, DiffOptions options);

    // Listing and changing stashes, as git stash does.

    /**
     * The stashes, newest first.
     */
    Either<ProcessError, List<Stash>> stashes();

    /**
     * What the stash {@code sha} stashed, like {@code git stash show}: the changed files, untracked ones too, a
     * summary of the changes, a blank line and the diff, read with {@code options}. Long output is cut off.
     */
    Either<ProcessError, DiffText> stashChanges(Sha sha, DiffOptions options);

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
     * the one it came from, as {@link #parentBranch} reads it.
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
     * The worktrees, the one shown first, then the others as git lists them, the main one first; none for a bare
     * repository itself, which has no working tree.
     */
    Either<ProcessError, List<Worktree>> worktrees();

    /**
     * Whether the worktree at {@code worktree} has changes that removing it would lose: staged, unstaged or
     * untracked files, not ignored ones.
     */
    Either<ProcessError, Boolean> hasChanges(Path worktree);

    /**
     * Checks {@code branch} out in a new worktree at {@code location}, which is taken relative to the working
     * tree of this one. Git refuses a branch that is checked out already, and a location that is not empty.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktree(Path location, BranchName branch);

    /**
     * Checks {@code startPoint}, a branch, a commit or any other revision, out in a new worktree at
     * {@code location}, taken as {@link #addWorktree} takes it, on no branch.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetachedWorktree(Path location, String startPoint);

    /**
     * Creates the branch {@code name} at {@code startPoint} and checks it out in a new worktree at {@code location},
     * taken as {@link #addWorktree} takes it. With {@code track}, the new branch follows {@code startPoint}, a
     * remote-tracking branch; a {@code parent} is recorded as the branch it was made from. Git refuses a name that
     * is a branch already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktreeWithNewBranch(
            Path location, BranchName name, String startPoint, Option<BranchName> parent, boolean track);

    /**
     * Removes the linked worktree at {@code location}, its directory included; its branch stays. Without
     * {@code force}, git refuses one with modified or untracked files, which is a
     * {@link GitError.WorktreeHasChanges}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force);

    /**
     * Forgets the worktrees whose directory is gone; their branches stay.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pruneWorktrees();

    /**
     * Locks the linked worktree at {@code location}, which keeps git from removing or pruning it, or unlocks it
     * when not {@code lock}. Git refuses the main worktree, and one that is locked already or not locked.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> lockWorktree(Path location, boolean lock);

    /**
     * Locks the linked worktree at {@code location} for {@code reason}, or without one when it is blank. Git refuses
     * the main worktree, and one that is locked already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> lockWorktree(Path location, String reason);

    // Editing files, as git edits commit messages.

    /**
     * Opens {@code files}, relative to the working tree, in the editor git uses for commit messages, and waits until
     * it is closed. It runs on the terminal, which the caller must have handed over. Fails with a
     * {@link GitError.EditorNotStarted} or {@link GitError.EditorFailed}, or a {@link ProcessError} when git names no
     * editor.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> edit(List<String> files);

    /**
     * Opens {@code message} in the editor git uses for commit messages, and gives the message it was saved as once
     * the editor is closed, with the command that ran. It runs on the terminal, which the caller must have handed
     * over. Fails as {@link #edit} does, or with a {@link GitError.TempFileFailed}.
     */
    Either<GitFailure<GitError>, Tuple2<CommitMessage, List<ExecutedCommand>>> editMessage(CommitMessage message);

    /**
     * Opens {@code text} in the editor git uses, in a temporary file called {@code fileName}, which tells the editor
     * what it is, and gives the text it was saved as once the editor is closed, with the command that ran. It runs on
     * the terminal, which the caller must have handed over. Fails as {@link #editMessage} does.
     */
    Either<GitFailure<GitError>, Tuple2<String, List<ExecutedCommand>>> editText(String fileName, String text);
}
