package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;

public sealed interface RewordMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RewordUpdate.update(model, this);
    }

    /**
     * The user asked to reword the commit at {@code index} in the commit log. Only the last commit
     * (index 0) can be reworded.
     */
    record Requested(int index) implements RewordMsg {
    }

    record Cancelled() implements RewordMsg {
    }

    record Confirmed(String summary, String description) implements RewordMsg {
    }

    record Done() implements RewordMsg {
    }

    record Failed(String message) implements RewordMsg {
    }
}
