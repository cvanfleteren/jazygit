package net.vanfleteren.jazygit.feature.worktree.switchto;

import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg.Requested;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching to another worktree, which the app then shows as if it had been started there, on the worktrees.
 */
public final class SwitchWorktreeUpdate {

    private SwitchWorktreeUpdate() {}

    public static Next update(Model model, SwitchWorktreeMsg msg) {
        return switch (msg) {
            case Requested(String location) -> requested(model, location);
        };
    }

    /**
     * Opens the worktree at {@code location} in place of this one, through the repositories the app knows; the
     * one shown already stays, and one whose directory is gone cannot be opened, which is said.
     */
    private static Next requested(Model model, String location) {
        return model.repo()
                .worktree(location)
                .map(worktree -> worktree.current()
                        ? Next.of(model.fail(AppError.refused("error.switchWorktree.shown", worktree.name())))
                        : worktree.prunable()
                                ? Next.of(model.fail(AppError.refused("error.switchWorktree.missing", worktree.name())))
                                : Next.of(model.clearError(), RecentReposCmd.SwitchTo.worktree(location)))
                .getOrElse(() -> Next.of(model));
    }
}
