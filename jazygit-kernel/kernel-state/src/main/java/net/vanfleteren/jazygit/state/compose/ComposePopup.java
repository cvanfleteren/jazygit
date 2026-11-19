package net.vanfleteren.jazygit.state.compose;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * A popup that writes text to send, as a comment or a review (ADR 115). The text lives here, in the model, as far as it
 * was typed or as it came back from the editor; {@link ComposeUpdate} handles what the user does in it, and this popup
 * says what differs: what sending it starts, and where going back goes. While it is being sent, it can be neither
 * changed, sent again nor left, so text that fails to send is not lost.
 */
public interface ComposePopup extends Popup {

    /**
     * The text as far as it was typed, or as it came back from the editor.
     */
    String text();

    /**
     * Whether it is being sent.
     */
    boolean sending();

    ComposePopup withText(String text);

    ComposePopup withSending(boolean sending);

    /**
     * The file the text is written in, in the editor, and what that is logged as.
     */
    EditorFile editorFile();

    /**
     * {@code model}, with this popup open and not being sent, once the user asked to send it: sending it, or why it
     * is not sent, or nothing when there is nothing to send.
     */
    Next send(Model model);

    /**
     * {@code model}, with this popup open and not being sent, once the user left it: without it, or back on the
     * popup it was opened from.
     */
    Model back(Model model);
}
