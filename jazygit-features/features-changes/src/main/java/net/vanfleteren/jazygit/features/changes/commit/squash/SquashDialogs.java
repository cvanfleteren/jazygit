package net.vanfleteren.jazygit.features.changes.commit.squash;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.changes.commit.PushedDialogs;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popup of squashing a commit.
 */
public final class SquashDialogs {

    /**
     * Asks to squash a commit into the one below it.
     */
    public static final ConfirmDialog.Spec<SquashPopup> SQUASH = ConfirmDialog.Spec.confirming(
            "squash-commit",
            SquashPopup.class,
            popup -> Messages.get("changes.dialog.squash.title"),
            popup -> PushedDialogs.withWarning(Messages.get("changes.dialog.squash.message"), popup.warning()));

    private SquashDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(PaneId.COMMITS, new ConfirmDialog<>(SQUASH, dispatch)));
    }
}
