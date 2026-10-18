package net.vanfleteren.jazygit.feature.branch.newbranch;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Creating a branch at the selected branch, remote branch or commit.
 */
public final class NewBranchAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("n", "help.branches.new", NewBranchMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected("n", "help.branches.new", NewBranchMsg.RequestedAtRemote::new))
                    .add(
                            Scopes.COMMITS,
                            Binding.onSelected(
                                    "n",
                                    "help.commits.newBranch",
                                    commit -> new NewBranchMsg.RequestedAtCommit(commit.sha()))),
            NewBranchDialogs::popups);

    private NewBranchAction() {}
}
