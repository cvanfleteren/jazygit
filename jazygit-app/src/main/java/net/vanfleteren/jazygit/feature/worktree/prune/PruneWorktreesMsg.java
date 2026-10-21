package net.vanfleteren.jazygit.feature.worktree.prune;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
