package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
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
     * The user asked for a new branch starting at the commit {@code sha}.
     */
    record RequestedAtCommit(Sha sha) implements NewBranchMsg {
    }

    /**
     * The user asked for a new branch starting at the remote branch {@code branch}.
     */
    record RequestedAtRemote(RemoteBranch branch) implements NewBranchMsg {
    }

    record Cancelled() implements NewBranchMsg {
    }

    /**
     * The user entered the name of the new branch.
     */
    record Confirmed(String name) implements NewBranchMsg {
    }
}
