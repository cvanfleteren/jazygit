package net.vanfleteren.jazygit.features.changes.commit.revert;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popup of reverting a commit.
 */
public final class RevertDialogs {

    /**
     * Asks to revert a commit; for a merge, it says that is against its first parent.
     */
    public static final ConfirmDialog.Spec<RevertPopup> REVERT = ConfirmDialog.Spec.confirming(
            "revert-commit",
            RevertPopup.class,
            popup -> Messages.get("changes.dialog.revert.title"),
            popup -> Messages.get(
                    popup.commit().isMerge() ? "changes.dialog.revert.mergeMessage" : "changes.dialog.revert.message",
                    popup.commit().shortSha(),
                    popup.commit().message()));

    private RevertDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(PaneId.COMMITS, new ConfirmDialog<>(REVERT, dispatch)));
    }
}
