package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface RenameBranchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RenameBranchUpdate.update(model, this);
    }

    /**
     * The user asked to rename {@code branch}; the new name is still to be entered.
     */
    record Requested(Branch.Name branch) implements RenameBranchMsg {
    }

    record Cancelled() implements RenameBranchMsg {
    }

    /**
     * The user entered the new name of the branch.
     */
    record Confirmed(String name) implements RenameBranchMsg {
    }
}
