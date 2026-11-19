package net.vanfleteren.jazygit.features.github.checkout;

import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Checking out a pull request listed on the pull requests screen.
 */
public sealed interface CheckoutPullRequestMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CheckoutPullRequestUpdate.update(model, this);
    }

    /**
     * The user asked to check out a pull request listed on the pull requests screen.
     */
    record Requested(PullRequestSummary pullRequest) implements CheckoutPullRequestMsg {}

    /**
     * The user asked to check out a pull request listed on the pull requests screen in a new worktree; where it
     * goes is still to be entered.
     */
    record WorktreeRequested(PullRequestSummary pullRequest) implements CheckoutPullRequestMsg {}
}
