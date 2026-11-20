package net.vanfleteren.jazygit.features.changes.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.CommitMessage;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Editing the message of the commit dialog in the editor.
 */
public sealed interface MessageEditorMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return MessageEditorUpdate.update(model, this);
    }

    /**
     * The user asked to edit the message of the open popup in the editor.
     */
    record Requested() implements MessageEditorMsg {}

    /**
     * The editor ran {@code commands}, and saved the message as {@code message}.
     */
    record Edited(CommitMessage message, List<ExecutedCommand> commands) implements MessageEditorMsg {}

    record Failed(Failure failure) implements MessageEditorMsg {}
}
