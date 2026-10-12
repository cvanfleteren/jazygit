package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface RewordMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RewordUpdate.update(model, this);
    }

    /**
     * The user asked to reword the commit at {@code index} in the commit log; any commit can be.
     */
    record Requested(int index) implements RewordMsg {
    }

    record Cancelled() implements RewordMsg {
    }

    record Confirmed(CommitMessage message) implements RewordMsg {
    }
}
