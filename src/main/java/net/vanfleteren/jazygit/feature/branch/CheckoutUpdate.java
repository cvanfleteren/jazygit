package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.NeedsStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.Requested;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashCancelled;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashConfirmed;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
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
            case NeedsStash(String branch) -> Next.of(model.openPopup(new StashCheckoutPopup(branch)));
            case StashCancelled() -> Next.of(model.withoutPopup(StashCheckoutPopup.class));
            case StashConfirmed() -> model.popup(StashCheckoutPopup.class).map(StashCheckoutPopup::branch)
                    .map(branch -> Next.of(model.withoutPopup(StashCheckoutPopup.class).clearError(),
                            new CheckoutWithStash(branch)))
                    .getOrElse(() -> Next.of(model));
        };
    }

    private static Next requested(Model model, String branch) {
        boolean known = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.exists(b -> b.name().equals(branch) && !b.current());
        return known ? Next.of(model.clearError(), new Checkout(branch)) : Next.of(model);
    }
}
