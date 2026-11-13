package net.vanfleteren.jazygit.feature.github.review;

import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.view.CommitFilesView;

/**
 * A comment of a review thread the cursor is on, with its thread and the files of the pull request it is of.
 *
 * @param on      its thread, with those files
 * @param comment the comment
 */
record OnComment(OnThread on, ReviewThread.Comment comment) {

    /**
     * The files of the pull request, as they are listed.
     */
    CommitFilesView view() {
        return on.view();
    }

    /**
     * The thread of the comment.
     */
    ReviewThread thread() {
        return on.thread();
    }

    /**
     * The number of the pull request.
     */
    int number() {
        return on.number();
    }

    /**
     * Whether the comment is being deleted.
     */
    boolean deleting() {
        return on.deleting(comment);
    }

    /**
     * Whether a reaction to the comment is being sent.
     */
    boolean reacting() {
        return view().reacting().contains(comment.id());
    }
}
