package net.vanfleteren.jazygit.features.branches.branch.merge;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Merging the selected branch into the checked out one.
 */
public final class MergeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "m",
                                    "branches.help.branches.merge",
                                    (BranchName branch) -> new MergeMsg.Requested(new BranchRef.Local(branch))))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "m",
                                    "branches.help.remoteBranches.merge",
                                    (RemoteBranch branch) -> new MergeMsg.Requested(branch.tracking()))),
            MergeDialogs::popups);

    private MergeAction() {}
}
