package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Update;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.NeedsStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.Requested;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashCancelled;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashConfirmed;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Checking out a branch.
 */
public final class CheckoutUpdate {

    private CheckoutUpdate() {
    }

    public static Next update(Model model, CheckoutMsg msg) {
        return switch (msg) {
            case Requested(String branch) -> requested(model, branch);
            // The checkout ran, and failed; the stash it needs is offered instead of the failure.
            case NeedsStash(var cmd, var commands) -> Update.refresh(OperationUpdate.done(model, cmd, commands)
                    .openPopupIfFree(new StashCheckoutPopup(cmd.branch())));
            case StashCancelled() -> Next.of(model.withoutPopup(StashCheckoutPopup.class));
            case StashConfirmed() -> Update.confirm(model, StashCheckoutPopup.class,
                    popup -> Option.of(new CheckoutWithStash(popup.branch())));
        };
    }

    private static Next requested(Model model, String branch) {
        return Branches.isOtherIdleBranch(model, branch) ? Update.start(model.clearError(), new Checkout(branch)) : Next.of(model);
    }
}
