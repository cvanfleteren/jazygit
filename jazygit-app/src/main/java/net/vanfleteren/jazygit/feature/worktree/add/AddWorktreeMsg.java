package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Checking a branch out in a new worktree, the branch an existing one or a new one.
 */
public sealed interface AddWorktreeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return AddWorktreeUpdate.update(model, this);
    }

    /**
     * The user asked to check {@code branch} out in a new worktree; where is still to be entered.
     */
    record Requested(Branch.Name branch) implements AddWorktreeMsg {}

    /**
     * The user asked for a new branch made from {@code base}, checked out in a new worktree; its name and where
     * are still to be entered.
     */
    record NewBranchRequested(Branch.Name base) implements AddWorktreeMsg {}

    /**
     * The user asked for a new branch that follows {@code remote}, checked out in a new worktree.
     */
    record NewBranchAtRemoteRequested(RemoteBranch remote) implements AddWorktreeMsg {}

    record NameCancelled() implements AddWorktreeMsg {}

    /**
     * The user confirmed the name of the new branch, as typed in the popup.
     */
    record NameConfirmed() implements AddWorktreeMsg {}

    record Cancelled() implements AddWorktreeMsg {}

    /**
     * The user confirmed where the new worktree goes, as typed in the popup.
     */
    record Confirmed() implements AddWorktreeMsg {}

    /**
     * {@code cmd} ran {@code commands} and added the worktree, which is at {@code location}.
     */
    record Added(AddWorktreeCmd cmd, String location, List<ExecutedCommand> commands) implements AddWorktreeMsg {}

    /**
     * The user agreed to switch to the worktree that was added.
     */
    record SwitchConfirmed() implements AddWorktreeMsg {}

    /**
     * The user stays in the worktree shown.
     */
    record SwitchCancelled() implements AddWorktreeMsg {}
}
