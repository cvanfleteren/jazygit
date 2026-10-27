package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Asking whether to stash the uncommitted changes, check out {@code branch} and pop the stash again.
 */
public record StashCheckoutPopup(BranchName branch) implements ConfirmablePopup {
    @Override
    public Option<CheckoutWithStash> confirm() {
        return Option.of(new CheckoutWithStash(branch));
    }
}
