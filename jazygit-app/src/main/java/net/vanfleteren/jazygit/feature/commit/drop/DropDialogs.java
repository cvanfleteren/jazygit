package net.vanfleteren.jazygit.feature.commit.drop;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popup of dropping a commit.
 */
public final class DropDialogs {

    /**
     * Asks to drop a commit.
     */
    public static final ConfirmDialog.Spec<DropPopup> DROP = new ConfirmDialog.Spec<>(
            "drop-commit",
            DropPopup.class,
            popup -> Messages.get("dialog.drop.title"),
            popup -> Messages.get(
                    "dialog.drop.message",
                    popup.commit().sha().shortSha(),
                    popup.commit().message()),
            DropMsg.Confirmed::new,
            DropMsg.Cancelled::new);

    private DropDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(DROP, dispatch)));
    }
}
