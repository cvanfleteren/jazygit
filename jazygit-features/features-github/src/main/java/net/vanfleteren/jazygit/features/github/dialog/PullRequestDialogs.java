package net.vanfleteren.jazygit.features.github.dialog;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.github.comments.CommentDialog;
import net.vanfleteren.jazygit.features.github.comments.CommentsDialog;
import net.vanfleteren.jazygit.features.github.edit.EditPullRequestDialog;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;

/**
 * The popups of showing a pull request, of reading its comments, of commenting on it, and of editing it.
 */
public final class PullRequestDialogs {

    private PullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        // A pull request is shown from the branches, or from the pull requests screen; its comments from the pull
        // request; a comment from the pull request or its comments; an edit from the pull request.
        return List.of(
                PopupSlot.returningToFocus(new PullRequestDialog(dispatch)),
                PopupSlot.returningToFocus(new CommentsDialog(dispatch)),
                PopupSlot.returningToFocus(new CommentDialog(dispatch)),
                PopupSlot.returningToFocus(new EditPullRequestDialog(dispatch)));
    }
}
