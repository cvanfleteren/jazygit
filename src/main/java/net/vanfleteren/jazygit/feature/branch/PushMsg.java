package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface PushMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PushUpdate.update(model, this);
    }

    /**
     * The user asked to push a branch.
     */
    record Requested(String branch) implements PushMsg {
    }

    record ForceCancelled() implements PushMsg {
    }

    /**
     * The user confirmed overwriting the diverged remote branch.
     */
    record ForceConfirmed() implements PushMsg {
    }

    record Done(String branch) implements PushMsg {
    }

    record Failed(String branch, String message) implements PushMsg {
    }
}
