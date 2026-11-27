package net.vanfleteren.jazygit.features.branches.branch.rebase;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Rebasing the checked out branch onto the selected one, or on its base branch (ADR 91).
 */
public final class RebaseAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "r",
                                    "branches.help.branches.rebase",
                                    (BranchName branch) -> new RebaseMsg.Requested(new BranchRef.Local(branch))))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "r",
                                    "branches.help.remoteBranches.rebase",
                                    (RemoteBranch branch) -> new RebaseMsg.Requested(branch.tracking()))),
            RebaseDialogs::popups);

    private RebaseAction() {}
}
