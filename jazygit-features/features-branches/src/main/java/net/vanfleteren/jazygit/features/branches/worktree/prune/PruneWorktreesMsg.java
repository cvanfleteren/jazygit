package net.vanfleteren.jazygit.features.branches.worktree.prune;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Forgetting the worktrees whose directory is gone.
 */
public sealed interface PruneWorktreesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PruneWorktreesUpdate.update(model, this);
    }

    /**
     * The user asked to forget the worktrees whose directory is gone.
     */
    record Requested() implements PruneWorktreesMsg {}
}
