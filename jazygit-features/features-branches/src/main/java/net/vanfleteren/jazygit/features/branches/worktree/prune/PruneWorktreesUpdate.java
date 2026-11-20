package net.vanfleteren.jazygit.features.branches.worktree.prune;

import net.vanfleteren.jazygit.core.repo.Worktree;
import net.vanfleteren.jazygit.features.branches.worktree.WorktreeError;
import net.vanfleteren.jazygit.features.branches.worktree.prune.PruneWorktreesMsg.Requested;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Forgetting the worktrees whose directory is gone. Nothing is lost, so it is not confirmed.
 */
public final class PruneWorktreesUpdate {

    private PruneWorktreesUpdate() {}

    public static Next update(Model model, PruneWorktreesMsg msg) {
        return switch (msg) {
            case Requested() -> requested(model);
        };
    }

    /**
     * Prunes the worktrees, once they are read; when none has its directory gone, that is said instead.
     */
    private static Next requested(Model model) {
        return model.repo()
                .worktrees()
                .toOption()
                .map(worktrees -> worktrees.exists(Worktree::prunable)
                        ? OperationUpdate.start(model.clearError(), new PruneWorktreesCmd())
                        : Next.of(model.fail(new WorktreeError.NothingToPrune())))
                .getOrElse(() -> Next.of(model));
    }
}
