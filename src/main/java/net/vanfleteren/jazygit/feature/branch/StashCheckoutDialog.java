package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.util.function.Consumer;

/**
 * Popup shown when checking out a branch while there are uncommitted changes: confirming stashes the
 * changes, checks out and pops the stash, cancelling cancels the checkout.
 */
public final class StashCheckoutDialog {

    public static final String ID = "stash-checkout";

    private StashCheckoutDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ConfirmDialog<StashCheckoutPopup> create(Consumer<Msg> dispatch) {
        return new ConfirmDialog<>(ID, StashCheckoutPopup.class,
                popup -> Messages.get("dialog.stashCheckout.title", popup.branch()),
                popup -> Messages.get("dialog.stashCheckout.message"),
                CheckoutMsg.StashConfirmed::new, CheckoutMsg.StashCancelled::new, dispatch);
    }
}
