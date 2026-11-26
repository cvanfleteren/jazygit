package net.vanfleteren.jazygit.features.github;

import java.util.Locale;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What went wrong with, or keeps from being done, an operation on a pull request on GitHub, or on a stack of them.
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
            return Phrase.of("github.error.mergePr.failed", branch.value(), message);
        }
    }

    /**
     * A pull request was asked for, but no other branch is on the remote for it to merge into.
     */
    record NoBase(BranchName branch) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.pullRequest.noBase", branch.value());
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, is not checked out while the working tree
     * has changes, which would come along.
     */
    record ChangesBeforeCheckout(String pullRequest) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.checkoutPr.changes", pullRequest);
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, gets no worktree of its own, as its branch
     * is checked out here, which git checks out in one worktree at a time.
     */
    record PullRequestCheckedOutHere(String pullRequest) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.checkoutPr.here", pullRequest);
        }
    }

    /**
     * Opening a branch on GitHub was asked for, but the branch is on no remote, or its upstream is gone.
     */
    record NotOnRemote(BranchName branch) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.browse.notOnRemote", branch.value());
        }
    }

    /**
     * Stack {@code stack} is not rebased, as {@code branch} of it, a branch or a pull request, is as {@code why} says
     * (ADR 74).
     */
    record StackNotRebased(int stack, String branch, StackRefusal why) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "github.error.stackNotRebased", Phrase.verbatim(String.valueOf(stack)), why.phrase(branch));
        }
    }

    /**
     * Stack {@code stack} is not pushed, as {@code branch} of it, a branch or a pull request, is as {@code why} says
     * (ADR 74).
     */
    record StackNotPushed(int stack, String branch, StackRefusal why) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.stackNotPushed", Phrase.verbatim(String.valueOf(stack)), why.phrase(branch));
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, cannot be checked out, as it is of
     * another repository.
     */
    record OtherRepository(String pullRequest) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.checkoutPr.otherRepository", pullRequest);
        }
    }

    /**
     * A key of the diff of a pull request was pressed that the cursor, the pull request or the viewer does not allow
     * (ADR 60).
     */
    record CannotReview(Why why) implements GitHubError {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.error.review." + why.name().toLowerCase(Locale.ROOT));
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

    /**
     * Why a stack is not rebased or pushed (ADR 74).
     */
    enum StackRefusal {
        /** The worktree has changes, which a rebase would bring onto the top instead of where they were. */
        CHANGES,
        /** Its base is not known. */
        NO_BASE,
        /** GitHub did not say the branch of an open pull request of it, which would be moved and pushed. */
        UNKNOWN_BRANCH,
        /** None of its pull requests is open: there is nothing to rebase. */
        NOTHING_OPEN,
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
        BEHIND_UNFOLLOWED;

        /**
         * Why, said of {@code branch}.
         */
        Phrase phrase(String branch) {
            return Phrase.of(
                    "github.error.stackNotRebased." + name().toLowerCase(Locale.ROOT), Phrase.verbatim(branch));
        }
    }
}
