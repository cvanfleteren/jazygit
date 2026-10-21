package net.vanfleteren.jazygit.feature.commit.revert;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The popup of reverting a commit: the commit dialog, starting out with the message git would write.
 */
public final class RevertDialogs {

    private RevertDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, CommitDialog.revert(dispatch)));
    }
}
