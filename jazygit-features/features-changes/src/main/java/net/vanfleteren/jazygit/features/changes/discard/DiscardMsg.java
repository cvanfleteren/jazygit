package net.vanfleteren.jazygit.features.changes.discard;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

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
    record Requested(List<FileEntry> files) implements DiscardMsg {}

    record Chosen(Scope scope) implements DiscardMsg {}
}
