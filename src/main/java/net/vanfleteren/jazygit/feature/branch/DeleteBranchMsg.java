package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.DeleteScope;
import io.vavr.collection.List;
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
    record Requested(String branch) implements DeleteBranchMsg {
    }

    record Cancelled() implements DeleteBranchMsg {
    }

    record Chosen(DeleteScope scope) implements DeleteBranchMsg {
    }

    record Deleted(String branch, List<String> commands) implements DeleteBranchMsg {
    }

    record Failed(String branch, String message) implements DeleteBranchMsg {
    }
}
