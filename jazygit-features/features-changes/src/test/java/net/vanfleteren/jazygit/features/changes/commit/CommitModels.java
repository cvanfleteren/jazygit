package net.vanfleteren.jazygit.features.changes.commit;

import net.vanfleteren.jazygit.core.git.model.CommitMessage;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Models of the commit message being written, for the tests of the features and the app.
 */
public final class CommitModels {

    private CommitModels() {}

    /**
     * {@code model} after the user typed {@code message} in the open {@link MessagePopup}.
     */
    public static Model typed(Model model, CommitMessage message) {
        return Update.update(model, new MessageEdited(message)).model();
    }
}
