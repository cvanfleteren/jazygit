package net.vanfleteren.jazygit.features.branches.branch.checkout;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Checking out the selected branch, local or remote, or the selected commit, detached (ADR 137).
 */
public final class CheckoutAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(" ", "branches.help.branches.checkout", CheckoutMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    " ", "branches.help.remoteBranches.checkout", CheckoutMsg.RemoteRequested::new))
                    // A commit, detached, in the commits pane and in the commits of a branch (ADR 137).
                    .add(
                            Scopes.COMMITS,
                            Binding.onSelected(
                                    " ",
                                    "branches.help.commits.checkout",
                                    commit -> new CheckoutMsg.CommitRequested(commit.sha())))
                    .add(
                            Scopes.BRANCH_COMMITS,
                            Binding.onSelected(
                                    " ",
                                    "branches.help.commits.checkout",
                                    commit -> new CheckoutMsg.CommitRequested(commit.sha())))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "w",
                                    "branches.help.remoteBranches.worktree",
                                    CheckoutMsg.RemoteInNewWorktreeRequested::new)),
            CheckoutDialogs::popups);

    private CheckoutAction() {}
}
