package net.vanfleteren.jazygit.feature.branch;

import java.util.List;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;

public sealed interface NewBranchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return NewBranchUpdate.update(model, this);
    }

    /**
     * The user asked for a new branch starting at {@code base}; the name is still to be entered.
     */
    record Requested(String base) implements NewBranchMsg {
    }

    record Cancelled() implements NewBranchMsg {
    }

    /**
     * The user entered the name of the new branch.
     */
    record Confirmed(String name) implements NewBranchMsg {
    }

    record Created(String name, List<String> commands) implements NewBranchMsg {
    }

    record Failed(String name, String message) implements NewBranchMsg {
    }
}
