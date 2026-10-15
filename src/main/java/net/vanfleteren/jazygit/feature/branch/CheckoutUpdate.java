package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Update;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Checkout;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutRemote;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.CheckoutWithStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.CheckedOut;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.NeedsStash;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.RemoteRequested;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.Requested;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashCancelled;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg.StashConfirmed;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;
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
            case Requested(Branch.Name branch) -> requested(model, branch);
            case RemoteRequested(RemoteBranch branch) -> remoteRequested(model, branch);
            // The checkout ran, and failed; the stash it needs is offered instead of the failure.
            case NeedsStash(var cmd, var commands) -> Update.refresh(OperationUpdate.done(model, cmd, commands)
                    .openPopupIfFree(new StashCheckoutPopup(cmd.branch())));
            case CheckedOut(var cmd, var commands) ->
                    OperationUpdate.succeeded(model.withBranchesTab(BranchesTab.LOCAL), cmd, commands);
            case StashCancelled() -> Next.of(model.withoutPopup(StashCheckoutPopup.class));
            case StashConfirmed() -> Update.confirm(model, StashCheckoutPopup.class,
                    popup -> Option.of(new CheckoutWithStash(popup.branch())));
        };
    }

    /**
     * Checks out the local branch that follows {@code remote}. Without one, creates it under the same name; when
     * a local branch that follows something else already has that name, asks for another.
     */
    private static Next remoteRequested(Model model, RemoteBranch remote) {
        if (model.remoteBranch(remote).isEmpty() || model.busy().containsKey(new BranchRef.Remote(remote))) {
            return Next.of(model);
        }
        return model.trackingBranch(remote)
                .map(local -> requested(model, local.name()))
                .getOrElse(() -> model.branch(remote.branch()).isDefined()
                        ? Update.ask(model, NewBranchPopup.following(remote))
                        : Update.start(model.clearError(), new CheckoutRemote(remote.branch(), remote)));
    }

    /**
     * Checks out {@code branch}; one that is checked out in another worktree git would refuse, so that is said
     * instead.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return Branches.checkedOutElsewhere(model, Operation.CHECKOUT, branch)
                .map(error -> Next.of(model.fail(error)))
                .getOrElse(() -> Branches.isOtherIdleBranch(model, branch)
                        ? Update.start(model.clearError(), new Checkout(branch))
                        : Next.of(model));
    }
}
