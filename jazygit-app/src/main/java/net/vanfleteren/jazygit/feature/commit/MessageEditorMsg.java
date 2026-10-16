package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Editing the message of the commit dialog in the editor.
 */
public sealed interface MessageEditorMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return MessageEditorUpdate.update(model, this);
    }

    /**
     * The user asked to edit {@code message}, as the dialog shows it, in the editor.
     */
    record Requested(CommitMessage message) implements MessageEditorMsg {}

    /**
     * The editor ran {@code commands}, and saved the message as {@code message}.
     */
    record Edited(CommitMessage message, List<ExecutedCommand> commands) implements MessageEditorMsg {}

    record Failed(Failure failure) implements MessageEditorMsg {}
}
