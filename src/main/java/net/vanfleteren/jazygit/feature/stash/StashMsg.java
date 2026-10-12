package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface StashMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StashUpdate.update(model, this);
    }

    /**
     * The user asked to stash the changes; the name is still to be entered.
     */
    record Requested() implements StashMsg {
    }

    record Cancelled() implements StashMsg {
    }

    /**
     * The user confirmed the dialog; a blank name asks for a generated one.
     */
    record Confirmed(String name) implements StashMsg {
    }

    record Done(List<String> commands) implements StashMsg {
    }

    record Failed(String message) implements StashMsg {
    }

    /**
     * The user asked to unstash the stash {@code ref}.
     */
    record PopRequested(String ref) implements StashMsg {
    }

    record Popped(List<String> commands) implements StashMsg {
    }

    record PopFailed(String message) implements StashMsg {
    }

    /**
     * The user asked to delete the stash {@code ref}; confirmation is still to be given.
     */
    record DropRequested(String ref) implements StashMsg {
    }

    record DropCancelled() implements StashMsg {
    }

    record DropConfirmed() implements StashMsg {
    }

    record Dropped(List<String> commands) implements StashMsg {
    }

    record DropFailed(String message) implements StashMsg {
    }
}
