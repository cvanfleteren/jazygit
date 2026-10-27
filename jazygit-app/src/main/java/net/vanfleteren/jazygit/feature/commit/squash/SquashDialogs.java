package net.vanfleteren.jazygit.feature.commit.squash;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popup of squashing a commit.
 */
public final class SquashDialogs {

    /**
     * Asks to squash a commit into the one below it.
     */
    public static final ConfirmDialog.Spec<SquashPopup> SQUASH = new ConfirmDialog.Spec<>(
            "squash-commit",
            SquashPopup.class,
            popup -> Messages.get("dialog.squash.title"),
            popup -> Messages.get("dialog.squash.message"),
            SquashMsg.Confirmed::new);

    private SquashDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(SQUASH, dispatch)));
    }
}
