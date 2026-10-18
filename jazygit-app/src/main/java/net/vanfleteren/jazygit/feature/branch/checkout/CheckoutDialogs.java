package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of checking out a branch.
 */
public final class CheckoutDialogs {

    /**
     * Asks to take the uncommitted changes along to the branch being checked out, through a stash.
     */
    public static final ConfirmDialog.Spec<StashCheckoutPopup> STASH_CHECKOUT = new ConfirmDialog.Spec<>(
            "stash-checkout",
            StashCheckoutPopup.class,
            popup -> Messages.get("dialog.stashCheckout.title", popup.branch().value()),
            popup -> Messages.get("dialog.stashCheckout.message"),
            CheckoutMsg.StashConfirmed::new,
            CheckoutMsg.StashCancelled::new);

    /**
     * Tells that the branch to check out is checked out in another worktree, and asks to switch to that one.
     */
    public static final ConfirmDialog.Spec<SwitchWorktreePopup> SWITCH_WORKTREE = new ConfirmDialog.Spec<>(
            "switch-worktree",
            SwitchWorktreePopup.class,
            popup -> Messages.get("dialog.switchWorktree.title", popup.branch().value()),
            popup ->
                    Messages.get("dialog.switchWorktree.message", popup.branch().value(), popup.worktree()),
            CheckoutMsg.SwitchWorktreeConfirmed::new,
            CheckoutMsg.SwitchWorktreeCancelled::new);

    private CheckoutDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(STASH_CHECKOUT, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(SWITCH_WORKTREE, dispatch)));
    }
}
