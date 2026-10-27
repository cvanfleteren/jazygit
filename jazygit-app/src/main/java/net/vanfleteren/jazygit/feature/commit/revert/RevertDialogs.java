package net.vanfleteren.jazygit.feature.commit.revert;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popup of reverting a commit.
 */
public final class RevertDialogs {

    /**
     * Asks to revert a commit; for a merge, it says that is against its first parent.
     */
    public static final ConfirmDialog.Spec<RevertPopup> REVERT = new ConfirmDialog.Spec<>(
            "revert-commit",
            RevertPopup.class,
            popup -> Messages.get("dialog.revert.title"),
            popup -> Messages.get(
                    popup.commit().isMerge() ? "dialog.revert.mergeMessage" : "dialog.revert.message",
                    popup.commit().shortSha(),
                    popup.commit().message()),
            RevertMsg.Confirmed::new);

    private RevertDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(REVERT, dispatch)));
    }
}
