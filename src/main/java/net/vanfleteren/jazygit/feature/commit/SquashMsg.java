package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface SquashMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SquashUpdate.update(model, this);
    }

    /**
     * The user asked to squash the commit at {@code index} in the commit log into the one below it.
     */
    record Requested(int index) implements SquashMsg {
    }

    record Cancelled() implements SquashMsg {
    }

    record Confirmed() implements SquashMsg {
    }
}
