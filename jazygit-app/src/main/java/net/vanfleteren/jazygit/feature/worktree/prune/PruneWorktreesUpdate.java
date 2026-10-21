package net.vanfleteren.jazygit.feature.worktree.prune;

import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesMsg.Requested;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

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
                        ? Update.start(model.clearError(), new PruneWorktreesCmd())
                        : Next.of(model.fail(new AppError.NothingToPrune())))
                .getOrElse(() -> Next.of(model));
    }
}
