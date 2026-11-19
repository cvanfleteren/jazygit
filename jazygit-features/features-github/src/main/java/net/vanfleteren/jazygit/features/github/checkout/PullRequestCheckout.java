package net.vanfleteren.jazygit.features.github.checkout;

import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.features.branches.branch.checkout.CheckoutRequest;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Checking out {@code pullRequest}, which is of the repository, where the user says (ADR 68).
 */
public record PullRequestCheckout(PullRequestSummary pullRequest) implements CheckoutRequest {

    @Override
    public String label() {
        return CheckoutPullRequestUpdate.name(pullRequest);
    }

    @Override
    public Next here(Model model) {
        return CheckoutPullRequestUpdate.here(model, pullRequest);
    }

    /**
     * Asks where its new worktree goes, as adding one for it asks: not when this worktree has its branch checked out,
     * nor while its worktree is being added already.
     */
    @Override
    public Next inNewWorktree(Model model) {
        return CheckoutPullRequestUpdate.inWorktree(model, pullRequest, true);
    }

    /**
     * Checking it out again, without asking which repository it is of: the main worktree may not have read its pull
     * requests yet.
     */
    @Override
    public Msg again() {
        return new CheckoutMsg.Placed(this);
    }
}
