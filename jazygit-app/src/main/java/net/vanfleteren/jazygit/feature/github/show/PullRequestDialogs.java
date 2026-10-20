package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.github.comment.CommentDialog;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The popups of showing a pull request, and of commenting on it.
 */
public final class PullRequestDialogs {

    private PullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        // A pull request is shown from the branches, or from the pull requests screen; a comment from the pull request.
        return List.of(
                PopupSlot.returningToFocus(new PullRequestDialog(dispatch)),
                PopupSlot.returningToFocus(new CommentDialog(dispatch)));
    }
}
