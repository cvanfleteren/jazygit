package net.vanfleteren.jazygit.feature.github.review;

import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.view.CommitFilesView;

/**
 * A comment of a review thread the cursor is on, with its thread and the files of the pull request it is of.
 *
 * @param view    the files of the pull request, as they are listed
 * @param thread  the thread of the comment
 * @param comment the comment
 */
record OnComment(CommitFilesView view, ReviewThread thread, ReviewThread.Comment comment) {

    /**
     * The number of the pull request.
     */
    int number() {
        return view.pullRequest().getOrElse(0);
    }

    /**
     * Whether the comment is being deleted.
     */
    boolean deleting() {
        return view.deleting().contains(comment.id());
    }

    /**
     * Whether a reaction to the comment is being sent.
     */
    boolean reacting() {
        return view.reacting().contains(comment.id());
    }
}
