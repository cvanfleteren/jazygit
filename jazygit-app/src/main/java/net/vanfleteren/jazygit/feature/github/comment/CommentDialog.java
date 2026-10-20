package net.vanfleteren.jazygit.feature.github.comment;

import static dev.tamboui.toolkit.Toolkit.textArea;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a comment on the pull request that is shown. Enter adds a line, Ctrl+S sends it, Ctrl+O opens it in
 * the editor and Escape goes back to the pull request. While it is being sent, it says so; when sending failed, it
 * says why, under the comment. The comment lives in the {@link Model}, as a {@link CommentPopup}, which every change is
 * sent to; the text area only keeps where its cursor is.
 */
public final class CommentDialog implements PopupView {

    public static final String ID = "pull-request-comment";

    // With Ctrl held, sends the comment; Enter adds a line.
    private static final char CONFIRM_KEY = 's';
    // With Ctrl held, opens the comment in the editor.
    private static final char EDITOR_KEY = 'o';
    private static final int WIDTH = 110;
    private static final int ROWS = 12;
    private static final int ERROR_LINES = 3;

    private final TextAreaState text = new TextAreaState();
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommentDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(CommentPopup.class).map(popup -> {
            // The text area shows what the model has: what was typed in it, or what came back from the editor.
            if (!text.text().equals(popup.text())) {
                text.setText(popup.text());
                text.moveCursorToEnd();
            }
            Element area = new SubmittableTextArea(
                            textArea(text)
                                    .title(Messages.get("dialog.comment.field"))
                                    .placeholder(Messages.get("dialog.comment.placeholder")),
                            text,
                            ID,
                            this::confirm)
                    .onChange(typed -> dispatch.accept(new CommentMsg.Edited(typed)))
                    .rows(ROWS)
                    .onKeyEvent(this::keys);
            // The error of sending it, or of the editor; a new try clears it.
            List<Element> content = List.of(area)
                    .appendAll(model.outcome()
                            .error()
                            .filter(error -> !popup.sending())
                            .map(error -> DialogFrame.wrapped(ErrorText.of(error), WIDTH, ERROR_LINES)
                                    .addClass(Theme.Classes.ERROR)));
            return new HintedDialog(
                    DialogFrame.of(
                            Messages.get("dialog.comment.title", String.valueOf(popup.number())),
                            WIDTH,
                            content.toJavaArray(Element[]::new)),
                    Messages.get(popup.sending() ? "dialog.comment.sending" : "dialog.comment.hint"));
        });
    }

    /**
     * Goes back on Escape, sends on {@link #CONFIRM_KEY} and opens the comment in the editor on {@link #EDITOR_KEY}.
     * The dialog around the text area only gets keys after the global handlers, and those take Escape and Ctrl+S too.
     */
    private EventResult keys(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new CommentMsg.Cancelled());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            confirm();
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(EDITOR_KEY)) {
            dispatch.accept(new CommentMsg.EditorRequested());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void confirm() {
        // A blank comment keeps the popup open, see CommentUpdate.
        dispatch.accept(new CommentMsg.Confirmed());
    }
}
