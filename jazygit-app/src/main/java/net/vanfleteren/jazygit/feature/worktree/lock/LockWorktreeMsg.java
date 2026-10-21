package net.vanfleteren.jazygit.feature.worktree.lock;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
