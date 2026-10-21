package net.vanfleteren.jazygit.feature.branch.delete;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface DeleteBranchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DeleteBranchUpdate.update(model, this);
    }

    /**
     * The user asked to delete a branch; where is still to be chosen.
     */
    record Requested(Branch.Name branch) implements DeleteBranchMsg {}

    record Cancelled() implements DeleteBranchMsg {}

    record Chosen(DeleteScope scope) implements DeleteBranchMsg {}

    /**
     * The user chose to remove the other worktree that has the branch checked out, and to delete the branch
     * locally once it is gone.
     */
    record WithWorktreeChosen() implements DeleteBranchMsg {}

    /**
     * The user asked to delete a remote branch; that is still to be confirmed.
     */
    record RemoteRequested(RemoteBranch branch) implements DeleteBranchMsg {}

    record RemoteCancelled() implements DeleteBranchMsg {}

    record RemoteConfirmed() implements DeleteBranchMsg {}
}
