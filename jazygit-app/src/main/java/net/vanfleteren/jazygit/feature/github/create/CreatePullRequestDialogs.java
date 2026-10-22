package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The popup of creating a pull request: what it merges into, and what it says.
 */
public final class CreatePullRequestDialogs {

    private CreatePullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new NewPullRequestDialog(dispatch)));
    }
}
