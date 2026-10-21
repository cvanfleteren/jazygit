package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Checking the selected branch out in a new worktree, or a new branch made from it, or from the selected remote
 * branch; or, from the worktrees, a new worktree starting anywhere.
 */
public final class AddWorktreeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected("w", "help.branches.newWorktree", AddWorktreeMsg.Requested::new))
                    .add(
                            Scopes.BRANCHES,
                            Binding.onSelected(
                                    "W", "help.branches.newBranchWorktree", AddWorktreeMsg.NewBranchRequested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "W",
                                    "help.branches.newBranchWorktree",
                                    AddWorktreeMsg.NewBranchAtRemoteRequested::new))
                    .add(
                            Scopes.WORKTREES,
                            Binding.always("n", "help.worktrees.new", AddWorktreeMsg.StartRequested::new)),
            AddWorktreeDialogs::popups);

    private AddWorktreeAction() {}
}
