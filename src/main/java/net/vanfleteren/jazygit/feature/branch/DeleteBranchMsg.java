package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;

public sealed interface DeleteBranchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DeleteBranchUpdate.update(model, this);
    }

    /**
     * The user asked to delete a branch; where is still to be chosen.
     */
    record Requested(String branch) implements DeleteBranchMsg {
    }

    record Cancelled() implements DeleteBranchMsg {
    }

    record Chosen(BranchCmd.DeleteBranch.DeleteScope scope) implements DeleteBranchMsg {
    }

    record Deleted(String branch) implements DeleteBranchMsg {
    }

    record Failed(String branch, String message) implements DeleteBranchMsg {
    }
}
