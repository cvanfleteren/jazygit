package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.Popup;

/**
 * A popup asking for a commit message, which can be edited in the editor. The message lives here, in the model, as
 * far as the user typed it: the dialog shows it, and sends a {@link MessageEdited} for every change.
 */
public interface MessagePopup extends Popup {

    /**
     * The message as far as it was typed, or as it came back from the editor.
     */
    CommitMessage message();

    MessagePopup withMessage(CommitMessage message);
}
