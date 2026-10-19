package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Editing the message of the commit dialog in the editor. The dialog stays open meanwhile, and shows the message
 * as it was saved once the editor is closed.
 */
public final class MessageEditorUpdate {

    private MessageEditorUpdate() {}

    public static Next update(Model model, MessageEditorMsg msg) {
        return switch (msg) {
            case MessageEditorMsg.Requested() ->
                model.popup(MessagePopup.class)
                        .map(popup -> Next.of(model.clearError(), new MessageEditorCmd(popup.message())))
                        .getOrElse(() -> Next.of(model));
            case MessageEditorMsg.Edited(CommitMessage message, List<ExecutedCommand> commands) -> {
                Model logged = CommandLog.append(model, MessageEditorCmd.EDIT_MESSAGE, commands);
                // Cancelled meanwhile, the dialog stays closed.
                yield Next.of(logged.popup(MessagePopup.class)
                        .map(popup -> logged.openPopup(popup.withMessage(message)))
                        .getOrElse(logged));
            }
            case MessageEditorMsg.Failed(Failure failure) ->
                Next.of(CommandLog.append(model, MessageEditorCmd.EDIT_MESSAGE, failure.commands())
                        .fail(new AppError.OperationFailed(MessageEditorCmd.EDIT_MESSAGE, Option.none(), failure)));
        };
    }
}
