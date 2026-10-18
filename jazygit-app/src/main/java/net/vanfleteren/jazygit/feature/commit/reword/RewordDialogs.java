package net.vanfleteren.jazygit.feature.commit.reword;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The popup of rewording a commit: the commit dialog, starting out with its message.
 */
public final class RewordDialogs {

    private RewordDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, CommitDialog.reword(dispatch)));
    }
}
