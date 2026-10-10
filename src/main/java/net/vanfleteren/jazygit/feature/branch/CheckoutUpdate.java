package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.Requested;

import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.Failed;

import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.Done;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashCancelled;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashConfirmed;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.NeedsStash;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Update.Next;
import java.util.List;
import java.util.Optional;

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
                    .map(branch -> Next.of(model.withoutPopup(StashCheckoutPopup.class).withError(Optional.empty()),
                            new CheckoutWithStash(branch)))
                    .orElseGet(() -> Next.of(model));
            // Status and branches show the new HEAD; the moved HEAD then reloads the commits.
            case Done(String branch, List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.checkout", commands));
            case Failed(String branch, String message) ->
                    Next.of(model.withError(Optional.of(Messages.get("error.checkout.failed", branch, message))));
        };
    }

    private static Next requested(Model model, String branch) {
        boolean known = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch) && !b.current());
        return known ? Next.of(model.withError(Optional.empty()), new Checkout(branch)) : Next.of(model);
    }
}
