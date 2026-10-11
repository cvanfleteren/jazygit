package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.util.function.Consumer;

/**
 * Popup asking to confirm squashing a commit into the one below it.
 */
public final class SquashDialog {

    public static final String ID = "squash-commit";

    private SquashDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ConfirmDialog<SquashPopup> create(Consumer<Msg> dispatch) {
        return new ConfirmDialog<>(ID, SquashPopup.class,
                popup -> Messages.get("dialog.squash.title"),
                popup -> Messages.get("dialog.squash.message"),
                SquashMsg.Confirmed::new, SquashMsg.Cancelled::new, dispatch);
    }
}
