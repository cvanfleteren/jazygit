package net.vanfleteren.jazygit.feature.files.all;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Switching the files pane between the changed files of the working tree and all its files (ADR 108).
 */
public sealed interface AllFilesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return AllFilesUpdate.update(model, this);
    }

    /**
     * The user switched between the changed files and all files.
     */
    record Toggled() implements AllFilesMsg {}
}
