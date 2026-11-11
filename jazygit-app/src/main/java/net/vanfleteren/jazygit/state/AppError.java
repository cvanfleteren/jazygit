package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * What the user is told went wrong, as data; the view turns it into text.
 */
public sealed interface AppError {
    /**
     * Why {@code branch} has no upstream: none was set, or it was deleted on the remote.
     */
    static AppError noUpstream(Branch branch) {
        return branch.goneUpstream()
                .<AppError>map(upstream -> new UpstreamGone(branch.name(), upstream.name()))
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
    record MergeFailed(BranchName branch, String message) implements AppError {}

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
     * The file or directory at {@code path}, listed with all files of the working tree, was asked to be staged or
     * discarded, but nothing in it has changed (ADR 108).
     */
    record Unchanged(String path) implements AppError {

        /**
         * That {@code files} have no changes: the {@code directory} they were asked for as, else the one file, else
         * the deepest directory that holds them all.
         */
        public static Unchanged of(List<FileEntry> files, Option<String> directory) {
            return new Unchanged(directory.getOrElse(() -> files.size() == 1
                    ? files.head().path()
                    : files.map(FileEntry::path).reduceOption(Unchanged::shared).getOrElse("")));
        }

        /**
         * The deepest directory that holds both {@code a} and {@code b}, or the root.
         */
        private static String shared(String a, String b) {
            List<String> left = List.of(a.split("/")).init();
            List<String> right = List.of(b.split("/")).init();
            return left.zip(right)
                    .takeWhile(pair -> pair._1.equals(pair._2))
                    .map(pair -> pair._1)
                    .mkString("/");
        }
    }

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
     * What the user typed as a revision names no commit.
     */
    record UnknownRevision(String text) implements AppError {}

    /**
     * A push was asked for, but the branch is only behind its upstream: there is nothing to push, and a force push
     * would drop the commits of others.
     */
    record BehindUpstream(BranchName branch) implements AppError {}

    /**
     * A pull, or a reset onto the upstream, was asked for, but the branch has no upstream.
     */
    record NoUpstream(BranchName branch) implements AppError {}

    /**
     * A pull, or a reset onto the upstream, was asked for, but the upstream of the branch was deleted on the
     * remote.
     *
     * @param upstream the short name the upstream had, e.g. {@code origin/feature}
     */
    record UpstreamGone(BranchName branch, String upstream) implements AppError {}

    /**
     * Renaming the upstream of the branch too was chosen, but the branch no longer follows that upstream, or it is no
     * longer on the remote, as the branches were read again while the dialog was open (ADR 90).
     *
     * @param upstream the short name of the upstream the dialog named, e.g. {@code origin/feature}
     */
    record UpstreamChanged(BranchName branch, String upstream) implements AppError {}

    /**
     * Comparing a branch with the main branch was asked for, but there is no main branch to compare it with:
     * none is known, or it is gone.
     */
    record NoBaseBranch(BranchName branch) implements AppError {}

    /**
     * Comparing a branch with the main branch was asked for, but it is the main branch.
     */
    record IsBaseBranch(BranchName branch) implements AppError {}

    /**
     * An operation on the checked out branch was asked for, but HEAD is detached.
     */
    record NoBranchCheckedOut() implements AppError {}

    /**
     * A pull of a branch that is not checked out was asked for, but its commits not yet pushed keep it from
     * being fast-forwarded.
     */
    record UnpushedCommits(BranchName branch) implements AppError {}

    /**
     * An operation was asked for on a branch that is checked out in another worktree, which keeps git from
     * doing it here.
     *
     * @param worktree the root of the worktree the branch is checked out in
     */
    record CheckedOutElsewhere(Operation operation, BranchName branch, String worktree) implements AppError {}

    /**
     * Stack {@code stack} is not rebased, as {@code branch} of it, a branch or a pull request, is as {@code why} says
     * (ADR 74).
     */
    record StackNotRebased(int stack, String branch, StackRefusal why) implements AppError {}

    /**
     * Stack {@code stack} is not pushed, as {@code branch} of it, a branch or a pull request, is as {@code why} says
     * (ADR 74).
     */
    record StackNotPushed(int stack, String branch, StackRefusal why) implements AppError {}

    /**
     * Why a stack is not rebased or pushed (ADR 74).
     */
    enum StackRefusal {
        /** The worktree has changes, which a rebase would bring onto the top instead of where they were. */
        CHANGES,
        /** Its base, or any open branch, is not known. */
        NO_BASE,
        /** Its base is not on the remote its branches are pushed to: it is fetched first. */
        BASE_NOT_FETCHED,
        /** Where a merged branch under it was is not known: its commits would be replayed. */
        MERGED_UNKNOWN,
        /** An open pull request of it has no local branch, which cannot be moved or pushed. */
        NOT_CHECKED_OUT,
        /** A branch of it is pushed to another remote than the others: they are pushed in one push. */
        OTHER_REMOTE,
        /** A branch of it is checked out in another worktree, under whose files it would move. */
        OTHER_WORKTREE,
        /** A branch of it is in a merge queue, which rewriting it takes it out of. */
        MERGE_QUEUE,
        /** A branch of it never had what is on its upstream, which a rebase and push would lose: it is pulled first. */
        BEHIND_REMOTE,
        /**
         * As {@link #BEHIND_REMOTE}, for a branch that does not follow its branch on the remote, which a pull then does
         * not bring in.
         */
        BEHIND_UNFOLLOWED
    }

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
    record Diverged(BranchName branch, RemoteBranch upstream) implements AppError {}

    /**
     * A pull request was asked for, but no other branch is on the remote for it to merge into.
     */
    record NoBase(BranchName branch) implements AppError {}

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
    record ChangesBeforeMove(BranchRef target) implements AppError {}

    /**
     * The commits of {@code branch} from {@code oldest} up are not moved, as {@code oldest} came in through
     * {@code merge} and so does its parent, {@code parent}: {@code branch} would be left on a commit of the merged
     * branch instead of one on its own line.
     */
    record MovedThroughMerge(Sha oldest, BranchName branch, Sha merge, Sha parent) implements AppError {}

    /**
     * Deleting a remote branch, or nuking a local one, was asked for, but it is the default branch.
     *
     * @param branch the branch: a remote one to delete, e.g. {@code origin/main}, or a local one to nuke
     */
    record DefaultBranch(BranchRef branch) implements AppError {}

    /**
     * Nuking {@code branch} did not delete all it was to delete (ADR 43).
     *
     * @param left     what is left, named as the dialog named it: the worktree, the branch, the remote branch
     * @param failures why, one for each step that failed
     */
    record NotAllNuked(BranchName branch, List<String> left, List<Failure> failures) implements AppError {}

    /**
     * Opening a branch on GitHub was asked for, but the branch is on no remote, or its upstream is gone.
     */
    record NotOnRemote(BranchName branch) implements AppError {}

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
     * A key of the diff of a pull request was pressed that the cursor, the pull request or the viewer does not allow
     * (ADR 60).
     */
    record CannotReview(Why why) implements AppError {

        /**
         * Why it cannot be done.
         */
        public enum Why {
            /**
             * Replying, resolving and opening a thread need the cursor on one.
             */
            NOT_ON_THREAD,
            /**
             * A range starts on a line, and a thread starts on a line, a range or the bar of a file.
             */
            NOT_ON_LINE,
            /**
             * The pull request moved on since its files were read: its lines number other lines now.
             */
            MOVED_ON,
            /**
             * The pull request is merged.
             */
            MERGED,
            /**
             * Its review threads are not read yet, or could not be.
             */
            NOT_READ,
            /**
             * GitHub does not let the viewer reply to the thread.
             */
            NO_REPLY,
            /**
             * GitHub does not let the viewer resolve the thread.
             */
            NO_RESOLVE,
            /**
             * GitHub does not let the viewer unresolve the thread.
             */
            NO_UNRESOLVE,
            /**
             * Something is being sent for the thread.
             */
            SENDING,
            /**
             * A thread is not started on a line the diff does not number, nor across hunks.
             */
            NOT_ONE_HUNK,
            /**
             * A thread is started in the diff, not in a version of its file shown in place of it (ADR 102): GitHub
             * takes only the lines of its hunks.
             */
            IN_VERSION,
            /**
             * GitHub does not let the viewer delete the comment (ADR 64).
             */
            NO_DELETE,
            /**
             * The comment is being deleted, or the first comment of the thread, which a reply goes to (ADR 64).
             */
            DELETING,
            /**
             * A collapsed thread of several comments does not say which of them to delete (ADR 64), or edit (ADR 65).
             */
            COLLAPSED,
            /**
             * GitHub does not let the viewer edit the comment (ADR 65).
             */
            NO_EDIT,
            /**
             * An edit does not empty a comment: deleting it does (ADR 65).
             */
            EMPTY,
            /**
             * GitHub does not let the viewer react to the comment, as on a locked conversation (ADR 86).
             */
            NO_REACT,
            /**
             * A reaction to the comment is being sent (ADR 86).
             */
            REACTING,
            /**
             * GitHub does not let the viewer approve their own pull request, nor ask for changes on it (ADR 100).
             */
            OWN_PULL_REQUEST,
            /**
             * The viewer has a pending review, started in the browser, which stops another (ADR 100).
             */
            PENDING_REVIEW,
            /**
             * Only an open pull request is approved, or asked for changes on (ADR 100).
             */
            NOT_OPEN,
            /**
             * Asking for changes needs a reason (ADR 100).
             */
            NO_REASON
        }
    }

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
