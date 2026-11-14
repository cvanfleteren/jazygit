package net.vanfleteren.jazygit.feature.worktree;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.open.OpenInEditorAction;
import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesAction;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeAction;

/**
 * The worktree operations: adding one for a branch, switching to one, removing one, pruning the ones whose
 * directory is gone, and locking one. Adding an operation is adding its subpackage, and listing its action here.
 */
public final class WorktreeFeature {

    /**
     * The operations, in the order their keys are listed in a panel. Switching from the worktrees to the other lists
     * of the branches pane is the branch feature's, as deleting a branch removes a worktree.
     */
    public static final Feature FEATURE = Feature.of(
            AddWorktreeAction.ACTION,
            SwitchWorktreeAction.ACTION,
            RemoveWorktreeAction.ACTION,
            PruneWorktreesAction.ACTION,
            LockWorktreeAction.ACTION,
            OpenInEditorAction.ACTION);

    private WorktreeFeature() {}
}
