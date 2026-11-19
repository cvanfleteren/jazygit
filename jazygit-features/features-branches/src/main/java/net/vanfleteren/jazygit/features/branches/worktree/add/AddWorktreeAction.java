package net.vanfleteren.jazygit.features.branches.worktree.add;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Checking the selected branch out in a new worktree, or a new branch made from it, or from the selected remote
 * branch; or, from the worktrees, a new worktree starting anywhere.
 */
public final class AddWorktreeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "w", "branches.help.branches.newWorktree", AddWorktreeMsg.Requested::new))
                    .add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "W",
                                    "branches.help.branches.newBranchWorktree",
                                    AddWorktreeMsg.NewBranchRequested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "W",
                                    "branches.help.branches.newBranchWorktree",
                                    AddWorktreeMsg.NewBranchAtRemoteRequested::new))
                    .add(
                            Scopes.WORKTREES,
                            Binding.always("n", "branches.help.worktrees.new", AddWorktreeMsg.StartRequested::new)),
            AddWorktreeDialogs::popups);

    private AddWorktreeAction() {}
}
