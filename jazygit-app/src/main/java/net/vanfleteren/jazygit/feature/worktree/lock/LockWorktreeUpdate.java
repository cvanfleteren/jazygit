package net.vanfleteren.jazygit.feature.worktree.lock;

import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeMsg.Requested;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Locking a worktree, which keeps git from removing or pruning it, or unlocking it.
 */
public final class LockWorktreeUpdate {

    private LockWorktreeUpdate() {}

    public static Next update(Model model, LockWorktreeMsg msg) {
        return switch (msg) {
            case Requested(String location) -> requested(model, location);
        };
    }

    /**
     * Locks the worktree at {@code location}, or unlocks it when it is locked. The main worktree git refuses,
     * which is said instead.
     */
    private static Next requested(Model model, String location) {
        return model.repo()
                .worktree(location)
                .map(worktree -> worktree.main()
                        ? Next.of(model.fail(new AppError.MainWorktreeNotLockable()))
                        : Update.start(model.clearError(), new LockWorktreeCmd(location, !worktree.isLocked())))
                .getOrElse(() -> Next.of(model));
    }
}
