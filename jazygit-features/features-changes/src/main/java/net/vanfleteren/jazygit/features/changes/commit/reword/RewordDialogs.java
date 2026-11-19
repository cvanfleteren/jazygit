package net.vanfleteren.jazygit.features.changes.commit.reword;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.changes.commit.CommitDialog;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;

/**
 * The popup of rewording a commit: the commit dialog, starting out with its message.
 */
public final class RewordDialogs {

    private RewordDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(PaneId.COMMITS, CommitDialog.reword(dispatch)));
    }
}
