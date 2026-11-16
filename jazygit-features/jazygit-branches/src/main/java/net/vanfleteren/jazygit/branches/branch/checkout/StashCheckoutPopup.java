package net.vanfleteren.jazygit.branches.branch.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.state.popup.ConfirmablePopup;

/**
 * Asking whether to stash the uncommitted changes, do {@code checkout} and pop the stash again.
 */
public record StashCheckoutPopup(StashableCheckout checkout) implements ConfirmablePopup {

    /**
     * Asking it for the checkout of {@code branch}.
     */
    public StashCheckoutPopup(BranchName branch) {
        this(new CheckoutCmd.Checkout(branch));
    }

    @Override
    public Option<GitOperationCmd> confirm() {
        return Option.of(checkout.withStash());
    }
}
