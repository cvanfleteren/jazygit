package net.vanfleteren.jazygit.features.changes.commit.reword;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.changes.commit.CommitDialog;
import net.vanfleteren.jazygit.features.changes.commit.PushedDialogs;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of rewording a commit: a warning when it is pushed already, then the commit dialog, starting out with its
 * message.
 */
public final class RewordDialogs {

    /**
     * Warns that the commit to reword is pushed already, before asking for its new message.
     */
    public static final ConfirmDialog.Spec<RewordPushedPopup> PUSHED = new ConfirmDialog.Spec<>(
            "reword-pushed",
            RewordPushedPopup.class,
            popup -> Messages.get("changes.dialog.pushed.title"),
            popup -> PushedDialogs.goOn(popup.warning()),
            RewordMsg.PushedConfirmed::new);

    private RewordDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(PaneId.COMMITS, new ConfirmDialog<>(PUSHED, dispatch)),
                PopupSlot.returningTo(PaneId.COMMITS, CommitDialog.reword(dispatch)));
    }
}
