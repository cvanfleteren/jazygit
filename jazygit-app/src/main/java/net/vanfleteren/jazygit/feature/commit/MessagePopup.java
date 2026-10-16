package net.vanfleteren.jazygit.feature.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.Popup;

/**
 * A popup asking for a commit message, which can be edited in the editor.
 */
public sealed interface MessagePopup extends Popup permits CommitPopup, RewordPopup {

    /**
     * The message the dialog is to show instead of what it shows, once it came back from the editor.
     */
    Option<CommitMessage> draft();

    MessagePopup withDraft(CommitMessage draft);
}
