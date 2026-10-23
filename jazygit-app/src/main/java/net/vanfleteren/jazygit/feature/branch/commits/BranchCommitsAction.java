package net.vanfleteren.jazygit.feature.branch.commits;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Viewing the commits of the selected branch, local or remote, and going back to the branches.
 */
public final class BranchCommitsAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
                    Scopes.BRANCHES,
                    Binding.onSelected(
                            Keymap.ENTER,
                            "help.branches.viewCommits",
                            name -> new BranchCommitsMsg.Opened(new BranchRef.Local(name))))
            .add(
                    Scopes.REMOTE_BRANCHES,
                    Binding.onSelected(
                            Keymap.ENTER,
                            "help.branches.viewCommits",
                            remote -> new BranchCommitsMsg.Opened(new BranchRef.Remote(remote))))
            .add(
                    Scopes.BRANCH_COMMITS,
                    Binding.always(Keymap.ESCAPE, "help.branchCommits.close", BranchCommitsMsg.Closed::new)));

    private BranchCommitsAction() {}
}
