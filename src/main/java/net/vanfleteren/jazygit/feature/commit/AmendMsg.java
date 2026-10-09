package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;

public sealed interface AmendMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return AmendUpdate.update(model, this);
    }

    /**
     * The user asked to amend the last commit with the staged files, or with all files if none is
     * staged; confirmation is still to be given.
     */
    record Requested() implements AmendMsg {
    }

    record Cancelled() implements AmendMsg {
    }

    record Confirmed() implements AmendMsg {
    }

    record Done() implements AmendMsg {
    }

    record Failed(String message) implements AmendMsg {
    }
}
