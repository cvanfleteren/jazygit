package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface RebaseMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RebaseUpdate.update(model, this);
    }

    /**
     * The user asked to rebase the checked out branch onto {@code onto}; how is still to be chosen.
     */
    record Requested(String onto) implements RebaseMsg {
    }

    record Cancelled() implements RebaseMsg {
    }

    /**
     * The user chose a plain rebase.
     */
    record Chosen() implements RebaseMsg {
    }
}
