package net.vanfleteren.jazygit.feature.github;

import java.util.Locale;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong with, or keeps from being done, an operation on a pull request on GitHub.
 */
public sealed interface GitHubError extends AppError {

    /**
     * GitHub did not merge the pull request of {@code branch}, which it was asked to do in the background.
     *
     * @param message why, as GitHub says it
     */
    record MergeFailed(BranchName branch, String message) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.mergePr.failed", branch.value(), message);
        }
    }

    /**
     * A pull request was asked for, but no other branch is on the remote for it to merge into.
     */
    record NoBase(BranchName branch) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pullRequest.noBase", branch.value());
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, is not checked out while the working tree
     * has changes, which would come along.
     */
    record ChangesBeforeCheckout(String pullRequest) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.checkoutPr.changes", pullRequest);
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, gets no worktree of its own, as its branch
     * is checked out here, which git checks out in one worktree at a time.
     */
    record PullRequestCheckedOutHere(String pullRequest) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.checkoutPr.here", pullRequest);
        }
    }

    /**
     * Opening a branch on GitHub was asked for, but the branch is on no remote, or its upstream is gone.
     */
    record NotOnRemote(BranchName branch) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.browse.notOnRemote", branch.value());
        }
    }

    /**
     * A key of the diff of a pull request was pressed that the cursor, the pull request or the viewer does not allow
     * (ADR 60).
     */
    record CannotReview(Why why) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.review." + why.name().toLowerCase(Locale.ROOT));
        }

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
}
