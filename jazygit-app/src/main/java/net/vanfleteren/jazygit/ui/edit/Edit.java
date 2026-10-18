package net.vanfleteren.jazygit.ui.edit;

/**
 * What a {@link LineCommand} does to the text: move the cursor, or delete a part of it. Positions are offsets in
 * the text, a line break counting as one.
 */
public sealed interface Edit {

    /**
     * Moves the cursor to {@code to}, leaving the text as it is.
     */
    record Move(int to) implements Edit {}

    /**
     * Deletes the text from {@code from} up to {@code to}, and leaves the cursor where it was deleted.
     */
    record Delete(int from, int to) implements Edit {}
}
