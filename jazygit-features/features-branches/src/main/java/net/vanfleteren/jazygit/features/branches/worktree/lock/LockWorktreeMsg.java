package net.vanfleteren.jazygit.features.branches.worktree.lock;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Locking a worktree, or unlocking it.
 */
public sealed interface LockWorktreeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return LockWorktreeUpdate.update(model, this);
    }

    /**
     * The user asked to lock the worktree at {@code location}, or to unlock it when it is locked.
     */
    record Requested(String location) implements LockWorktreeMsg {}
}
