package net.vanfleteren.jazygit.state;

/**
 * What the user does with the filter of the tree the files pane shows: that of the working tree, or of the changes
 * open in its place (ADR 105).
 */
public sealed interface FileFilterMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FileFilterUpdate.update(model, this);
    }

    /**
     * The user opened the line above the tree to type the filter in.
     */
    record Opened() implements FileFilterMsg {}

    /**
     * The user changed the text of the open line to {@code text}.
     */
    record Edited(String text) implements FileFilterMsg {}

    /**
     * The user closed the line, putting back the filter from before it was opened.
     */
    record Cancelled() implements FileFilterMsg {}

    /**
     * The user closed the line, keeping the filter it holds.
     */
    record Submitted() implements FileFilterMsg {}

    /**
     * The user cleared the filter, to see all files again.
     */
    record Cleared() implements FileFilterMsg {}
}
