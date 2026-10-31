package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textArea;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import io.vavr.collection.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.HintedDialog;

/**
 * What the popups that write a comment on a pull request have alike (ADR 5, ADR 60): the lines the comment is about,
 * dimmed, a text area where Enter adds a line, Ctrl+S sends it, Ctrl+O opens it in the editor and Escape goes back,
 * and why sending it failed, under it. The comment lives in the {@link Model}, which every change is sent to; the text
 * area only keeps where its cursor is.
 */
public final class CommentComposer {

    // With Ctrl held, sends the comment; Enter adds a line.
    private static final char CONFIRM_KEY = 's';
    // With Ctrl held, opens the comment in the editor.
    private static final char EDITOR_KEY = 'o';
    private static final int WIDTH = 110;
    private static final int ROWS = 12;
    private static final int ERROR_LINES = 3;
    // The most lines shown of what the comment is about.
    private static final int QUOTED = 8;

    private final TextAreaState text = new TextAreaState();
    private final String id;
    private final Consumer<Msg> dispatch;

    /**
     * @param id       the id of the text area
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommentComposer(String id, Consumer<Msg> dispatch) {
        this.id = id;
        this.dispatch = dispatch;
    }

    /**
     * The popup that writes {@code comment}, titled {@code title}, under {@code quoted}, sending what the user does as
     * {@code actions} makes it.
     */
    public Element render(Model model, String title, List<String> quoted, Comment comment, Actions actions) {
        // The text area shows what the model has: what was typed in it, or what came back from the editor.
        if (!text.text().equals(comment.text())) {
            text.setText(comment.text());
            text.moveCursorToEnd();
        }
        // Without a title, the text area has no border of its own: the dialog's is enough.
        Element area = new SubmittableTextArea(
                        textArea(text).placeholder(Messages.get("dialog.comment.placeholder")),
                        text,
                        id,
                        () -> dispatch.accept(actions.confirmed().get()))
                .onChange(typed -> dispatch.accept(actions.edited().apply(typed)))
                .rows(ROWS)
                .onKeyEvent(event -> keys(event, actions));
        List<Element> about = quoted.size() > QUOTED
                ? quoted.take(QUOTED).append("…").map(line -> about(line))
                : quoted.map(CommentComposer::about);
        // The error of sending it, or of the editor; a new try clears it.
        List<Element> content = about.append(area)
                .appendAll(model.outcome()
                        .error()
                        .filter(error -> !comment.sending())
                        .map(error -> DialogFrame.wrapped(ErrorText.of(error), WIDTH, ERROR_LINES)
                                .addClass(Theme.Classes.ERROR)));
        return new HintedDialog(
                DialogFrame.of(title, WIDTH, content.toJavaArray(Element[]::new)),
                Messages.get(comment.sending() ? "dialog.comment.sending" : "dialog.comment.hint"));
    }

    private static Element about(String line) {
        return text(line).addClass(Theme.Classes.DIM);
    }

    /**
     * Goes back on Escape, sends on {@link #CONFIRM_KEY} and opens the comment in the editor on {@link #EDITOR_KEY}.
     * The dialog around the text area only gets keys after the global handlers, and those take Escape and Ctrl+S too.
     */
    private EventResult keys(KeyEvent event, Actions actions) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(actions.cancelled().get());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            // A blank comment keeps the popup open.
            dispatch.accept(actions.confirmed().get());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(EDITOR_KEY)) {
            dispatch.accept(actions.editor().get());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * The comment as the model has it.
     *
     * @param text    as far as it was typed
     * @param sending whether it is being sent
     */
    public record Comment(String text, boolean sending) {}

    /**
     * The messages of what the user does in the popup.
     *
     * @param edited    the comment changed to a text
     * @param confirmed send it
     * @param cancelled go back
     * @param editor    write it in the editor
     */
    public record Actions(
            Function<String, Msg> edited, Supplier<Msg> confirmed, Supplier<Msg> cancelled, Supplier<Msg> editor) {}
}
