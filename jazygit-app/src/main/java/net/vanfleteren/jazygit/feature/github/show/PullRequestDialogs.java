package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The popup of showing a pull request.
 */
public final class PullRequestDialogs {

    private PullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        // A pull request is shown from the branches, or from the pull requests screen.
        return List.of(PopupSlot.returningToFocus(new PullRequestDialog(dispatch)));
    }
}
