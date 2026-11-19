package net.vanfleteren.jazygit.features.branches.branch.commits;

import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Viewing the commits of the selected branch, local or remote, and going back to the branches.
 */
public final class BranchCommitsAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
                    Scopes.BRANCHES,
                    Binding.onSelected(
                            Keymap.ENTER,
                            "branches.help.branches.viewCommits",
                            name -> new BranchCommitsMsg.Opened(new BranchRef.Local(name))))
            .add(
                    Scopes.REMOTE_BRANCHES,
                    Binding.onSelected(
                            Keymap.ENTER,
                            "branches.help.branches.viewCommits",
                            remote -> new BranchCommitsMsg.Opened(new BranchRef.Remote(remote))))
            .add(
                    Scopes.BRANCH_COMMITS,
                    Binding.always(Keymap.ESCAPE, "branches.help.branchCommits.close", BranchCommitsMsg.Closed::new)));

    private BranchCommitsAction() {}
}
