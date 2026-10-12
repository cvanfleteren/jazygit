package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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

    /**
     * The user asked for a new branch starting at the commit at {@code index} in the commit log.
     */
    record RequestedAtCommit(int index) implements NewBranchMsg {
    }

    record Cancelled() implements NewBranchMsg {
    }

    /**
     * The user entered the name of the new branch.
     */
    record Confirmed(String name) implements NewBranchMsg {
    }
}
