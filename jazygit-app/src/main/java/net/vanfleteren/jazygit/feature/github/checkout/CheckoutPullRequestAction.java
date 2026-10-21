package net.vanfleteren.jazygit.feature.github.checkout;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Checking out the pull request selected on the pull requests screen, in place or in a new worktree.
 */
public final class CheckoutPullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> Scopes.PULL_REQUESTS.foldLeft(
            keys,
            (added, scope) -> added.add(
                    scope,
                    Binding.onSelected(" ", "help.prs.checkout", CheckoutPullRequestMsg.Requested::new),
                    Binding.onSelected(
                            "w", "help.prs.checkoutWorktree", CheckoutPullRequestMsg.WorktreeRequested::new))));

    private CheckoutPullRequestAction() {}
}
