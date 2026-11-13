package net.vanfleteren.jazygit.state.compose;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposeCmd.OpenInEditor;
import net.vanfleteren.jazygit.state.operation.CommandLog;

/**
 * Writing text in the open {@link ComposePopup}, in the popup or in the editor, sending it and leaving it (ADR 115).
 * While it is being sent, the text stays as it is sent, and the popup stays open: sending it again, leaving it or
 * opening the editor do nothing. What sending it starts, and where leaving it goes, the popup says.
 */
public final class ComposeUpdate {

    private ComposeUpdate() {}

    public static Next update(Model model, ComposeMsg msg) {
        return switch (msg) {
            case ComposeMsg.Edited(String text) ->
                Next.of(editable(model)
                        .map(popup -> model.openPopup(popup.withText(text)))
                        .getOrElse(model));
            case ComposeMsg.Confirmed() ->
                editable(model).map(popup -> popup.send(model)).getOrElse(() -> Next.of(model));
            case ComposeMsg.Cancelled() ->
                Next.of(editable(model).map(popup -> popup.back(model)).getOrElse(model));
            case ComposeMsg.EditorRequested() ->
                editable(model)
                        .map(popup -> Next.of(model.clearError(), new OpenInEditor(popup.editorFile(), popup.text())))
                        .getOrElse(() -> Next.of(model));
            case ComposeMsg.EditorClosed(EditorFile file, String text, var commands) ->
                Next.of(written(CommandLog.append(model, file.operation(), commands), text));
            case ComposeMsg.EditorFailed(EditorFile file, var failure) ->
                Next.of(CommandLog.append(model, file.operation(), failure.commands())
                        .fail(new AppError.OperationFailed(file.operation(), Option.none(), failure)));
        };
    }

    /**
     * {@code model} once sending the popup of type {@code type} failed: it is open for a new try, with its text, if it
     * is still open.
     */
    public static Model unsent(Model model, Class<? extends ComposePopup> type) {
        return model.popup(type)
                .map(popup -> model.openPopup(popup.withSending(false)))
                .getOrElse(model);
    }

    /**
     * {@code model} with the popup holding {@code text}, as it came back from the editor. Left meanwhile, the popup
     * stays closed.
     */
    private static Model written(Model model, String text) {
        return model.popup(ComposePopup.class)
                .map(popup -> model.openPopup(popup.withText(text)))
                .getOrElse(model);
    }

    /**
     * The popup, if it is open and not being sent.
     */
    private static Option<ComposePopup> editable(Model model) {
        return model.popup(ComposePopup.class).filter(popup -> !popup.sending());
    }
}
