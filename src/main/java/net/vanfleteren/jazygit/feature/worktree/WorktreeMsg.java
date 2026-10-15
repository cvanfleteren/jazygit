package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Adding, removing and switching to worktrees. A worktree is named by its location, the root of its working tree.
 */
public sealed interface WorktreeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return WorktreeUpdate.update(model, this);
    }

    /**
     * The user asked to check {@code branch} out in a new worktree; where is still to be entered.
     */
    record NewRequested(Branch.Name branch) implements WorktreeMsg {
    }

    record NewCancelled() implements WorktreeMsg {
    }

    /**
     * The user entered where the new worktree goes.
     */
    record NewConfirmed(String location) implements WorktreeMsg {
    }

    /**
     * {@code cmd} ran {@code commands} and added the worktree.
     */
    record Added(WorktreeCmd.Add cmd, List<ExecutedCommand> commands) implements WorktreeMsg {
    }

    /**
     * The user asked to show the worktree at {@code location} instead of this one.
     */
    record SwitchRequested(String location) implements WorktreeMsg {
    }

    /**
     * The user asked to remove the worktree at {@code location}.
     */
    record RemoveRequested(String location) implements WorktreeMsg {
    }

    record RemoveCancelled() implements WorktreeMsg {
    }

    record RemoveConfirmed() implements WorktreeMsg {
    }

    /**
     * Git refused the removal {@code cmd}, which ran {@code commands}, because the worktree has changes that
     * removing it would lose.
     */
    record NeedsForce(WorktreeCmd.Remove cmd, List<ExecutedCommand> commands) implements WorktreeMsg {
    }

    record ForceCancelled() implements WorktreeMsg {
    }

    /**
     * The user agreed to lose the changes of the worktree by removing it anyway.
     */
    record ForceConfirmed() implements WorktreeMsg {
    }
}
