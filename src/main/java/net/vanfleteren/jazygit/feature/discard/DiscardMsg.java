package net.vanfleteren.jazygit.feature.discard;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface DiscardMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DiscardUpdate.update(model, this);
    }

    /**
     * What is thrown away.
     */
    enum Scope {
        /**
         * Staged and unstaged changes, and untracked files.
         */
        ALL,
        /**
         * Unstaged changes and untracked files; what is staged stays.
         */
        UNSTAGED
    }

    /**
     * The user asked to discard the changes of the given files; what exactly is still to be chosen.
     */
    record Requested(List<FileEntry> files) implements DiscardMsg {
    }

    record Cancelled() implements DiscardMsg {
    }

    record Chosen(Scope scope) implements DiscardMsg {
    }

    record Done(List<String> commands) implements DiscardMsg {
    }

    record Failed(String message) implements DiscardMsg {
    }
}
