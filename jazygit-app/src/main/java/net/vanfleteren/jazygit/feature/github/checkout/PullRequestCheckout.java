package net.vanfleteren.jazygit.feature.github.checkout;

import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutRequest;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update.Next;

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

    @Override
    public WorktreeSource inNewWorktree() {
        return new WorktreeSource.PullRequest(pullRequest);
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
