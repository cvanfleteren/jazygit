package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.util.function.Consumer;

/**
 * Popup warning that a branch has diverged from its remote branch, and asking to force push it.
 */
public final class ForcePushDialog {

    public static final String ID = "force-push";

    private ForcePushDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ConfirmDialog<ForcePushPopup> create(Consumer<Msg> dispatch) {
        return new ConfirmDialog<>(ID, ForcePushPopup.class,
                popup -> Messages.get("dialog.forcePush.title", popup.branch()),
                popup -> Messages.get("dialog.forcePush.message"),
                PushMsg.ForceConfirmed::new, PushMsg.ForceCancelled::new, dispatch);
    }
}
