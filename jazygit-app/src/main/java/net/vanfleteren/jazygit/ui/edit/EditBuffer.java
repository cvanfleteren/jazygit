package net.vanfleteren.jazygit.ui.edit;

/**
 * Text being typed, with the cursor at offset {@code cursor} in it.
 */
public record EditBuffer(String text, int cursor) {

    /**
     * The text and cursor after {@code edit}.
     */
    public EditBuffer apply(Edit edit) {
        return switch (edit) {
            case Edit.Move(int to) -> new EditBuffer(text, to);
            case Edit.Delete(int from, int to) -> new EditBuffer(text.substring(0, from) + text.substring(to), from);
        };
    }
}
