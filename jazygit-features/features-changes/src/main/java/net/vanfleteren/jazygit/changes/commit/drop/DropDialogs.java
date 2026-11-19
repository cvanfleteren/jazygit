package net.vanfleteren.jazygit.changes.commit.drop;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popup of dropping a commit.
 */
public final class DropDialogs {

    /**
     * Asks to drop a commit.
     */
    public static final ConfirmDialog.Spec<DropPopup> DROP = ConfirmDialog.Spec.confirming(
            "drop-commit",
            DropPopup.class,
            popup -> Messages.get("changes.dialog.drop.title"),
            popup -> Messages.get(
                    "changes.dialog.drop.message",
                    popup.commit().sha().shortSha(),
                    popup.commit().message()));

    private DropDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(PaneId.COMMITS, new ConfirmDialog<>(DROP, dispatch)));
    }
}
