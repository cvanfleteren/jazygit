package net.vanfleteren.jazygit.feature.worktree.switchto;

import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg.Requested;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching to another worktree, which the app then shows as if it had been started there.
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
     * one shown already stays.
     */
    private static Next requested(Model model, String location) {
        return model.repo()
                .worktree(location)
                .filter(worktree -> !worktree.current())
                .map(worktree -> Next.of(model.clearError(), new RecentReposCmd.SwitchTo(location)))
                .getOrElse(() -> Next.of(model));
    }
}
