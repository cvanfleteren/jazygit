package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.util.function.Consumer;

/**
 * Popup asking to confirm amending the last commit.
 */
public final class AmendDialog {

    public static final String ID = "amend-commit";

    private AmendDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ConfirmDialog<AmendPopup> create(Consumer<Msg> dispatch) {
        return new ConfirmDialog<>(ID, AmendPopup.class,
                popup -> Messages.get("dialog.amend.title"),
                popup -> Messages.get("dialog.amend.message"),
                AmendMsg.Confirmed::new, AmendMsg.Cancelled::new, dispatch);
    }
}
