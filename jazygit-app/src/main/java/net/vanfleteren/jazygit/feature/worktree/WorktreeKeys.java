package net.vanfleteren.jazygit.feature.worktree;

import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for working with worktrees.
 */
public final class WorktreeKeys {

    private WorktreeKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                        Scopes.BRANCHES,
                        Binding.onSelected("w", "help.branches.newWorktree", AddWorktreeMsg.Requested::new))
                .add(
                        Scopes.WORKTREES,
                        Binding.onSelected(
                                " ",
                                "help.worktrees.switch",
                                worktree -> new SwitchWorktreeMsg.Requested(worktree.location())),
                        Binding.onSelected(
                                "d",
                                "help.worktrees.remove",
                                worktree -> new RemoveWorktreeMsg.Requested(worktree.location())),
                        Binding.always("[", "help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
                        Binding.always("]", "help.branches.nextTab", () -> new BranchesTabMsg.Switched(1)));
    }
}
