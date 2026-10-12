package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface FetchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FetchUpdate.update(model, this);
    }

    /**
     * Periodic prompt to fetch from the remotes.
     */
    record Tick() implements FetchMsg {
    }

    record Done(List<String> commands) implements FetchMsg {
    }

    record Failed(String message) implements FetchMsg {
    }
}
