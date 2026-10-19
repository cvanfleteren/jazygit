package net.vanfleteren.jazygit.state;

/**
 * A popup asking for one line of text. The text lives here, in the model, as far as the user typed it: the dialog
 * shows it, and sends a {@link Msg.TextEdited} for every change.
 */
public interface TextPopup extends Popup {

    /**
     * The text as far as it was typed, or what the dialog started out with.
     */
    String text();

    TextPopup withText(String text);
}
