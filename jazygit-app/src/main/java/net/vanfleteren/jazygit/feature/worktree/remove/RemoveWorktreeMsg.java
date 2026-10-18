package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Removing a worktree, named by its location, the root of its working tree.
 */
public sealed interface RemoveWorktreeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RemoveWorktreeUpdate.update(model, this);
    }

    /**
     * The user asked to remove the worktree at {@code location}.
     */
    record Requested(String location) implements RemoveWorktreeMsg {}

    record Cancelled() implements RemoveWorktreeMsg {}

    record Confirmed() implements RemoveWorktreeMsg {}

    /**
     * Git refused the removal {@code cmd}, which ran {@code commands}, because the worktree has changes that
     * removing it would lose.
     */
    record NeedsForce(RemoveWorktreeCmd cmd, List<ExecutedCommand> commands) implements RemoveWorktreeMsg {}

    record ForceCancelled() implements RemoveWorktreeMsg {}

    /**
     * The user agreed to lose the changes of the worktree by removing it anyway.
     */
    record ForceConfirmed() implements RemoveWorktreeMsg {}
}
