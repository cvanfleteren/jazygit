package net.vanfleteren.jazygit.state.compose;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * What the user does in the open {@link ComposePopup}: writing its text, in the popup or in the editor, sending it, or
 * leaving it (ADR 115). Only one popup is open at a time, so these name none.
 */
public sealed interface ComposeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ComposeUpdate.update(model, this);
    }

    /**
     * The user changed the text to {@code text}.
     */
    record Edited(String text) implements ComposeMsg {}

    /**
     * The user asked to send the text.
     */
    record Confirmed() implements ComposeMsg {}

    /**
     * The user left the popup, by Escape.
     */
    record Cancelled() implements ComposeMsg {}

    /**
     * The user asked to write the text in the editor.
     */
    record EditorRequested() implements ComposeMsg {}

    /**
     * The editor ran {@code commands} on {@code file}, and saved the text as {@code text}.
     */
    record EditorClosed(EditorFile file, String text, List<ExecutedCommand> commands) implements ComposeMsg {}

    /**
     * The editor failed on {@code file}.
     */
    record EditorFailed(EditorFile file, Failure failure) implements ComposeMsg {}
}
