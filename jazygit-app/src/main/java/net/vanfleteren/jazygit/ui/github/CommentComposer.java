package net.vanfleteren.jazygit.ui.github;

import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textArea;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.compose.ComposeMsg;
import net.vanfleteren.jazygit.ui.text.ErrorText;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.ui.widgets.SubmittableTextArea;

/**
 * What the popups that write a comment on a pull request have alike (ADR 5, ADR 60): the lines the comment is about,
 * dimmed, a text area where Enter adds a line, Ctrl+S sends it, Ctrl+O opens it in the editor and Escape goes back,
 * and why sending it failed, under it. The comment lives in the {@link Model}, as a
 * {@link net.vanfleteren.jazygit.state.compose.ComposePopup}, which every change is sent to as a {@link ComposeMsg}
 * (ADR 115); the text area only keeps where its cursor is.
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
     * The popup that writes {@code comment}, titled {@code title}, under {@code quoted}.
     */
    public Element render(Model model, String title, List<String> quoted, Comment comment) {
        return render(model, title, List.empty(), quoted, Option.none(), Option.none(), comment, Hints.COMMENT);
    }

    /**
     * The popup that writes {@code comment}, titled {@code title}, under {@code fields}, which come first and choose
     * how it is sent, then {@code quoted} and then {@code notice}, which stands out, with {@code hints} below it. The fields take their keys before {@link #keys}. With
     * {@code textTitle}, the text is in a box of its own under that title, set apart from the fields (ADR 100).
     */
    public Element render(
            Model model,
            String title,
            List<Element> fields,
            List<String> quoted,
            Option<String> notice,
            Option<String> textTitle,
            Comment comment,
            Hints hints) {
        // The text area shows what the model has: what was typed in it, or what came back from the editor.
        if (!text.text().equals(comment.text())) {
            text.setText(comment.text());
            text.moveCursorToEnd();
        }
        // Without a title, the text area has no border of its own: the dialog's is enough.
        TextAreaElement plain = textArea(text).placeholder(Messages.get("dialog.comment.placeholder"));
        Element area = new SubmittableTextArea(
                        textTitle.map(plain::title).getOrElse(plain),
                        text,
                        id,
                        () -> dispatch.accept(new ComposeMsg.Confirmed()))
                .onChange(typed -> dispatch.accept(new ComposeMsg.Edited(typed)))
                .rows(ROWS)
                .onKeyEvent(this::keys);
        List<Element> about = quoted.size() > QUOTED
                ? quoted.take(QUOTED).append("…").map(line -> about(line))
                : quoted.map(CommentComposer::about);
        // The error of sending it, or of the editor; a new try clears it.
        List<Element> content = fields.appendAll(about)
                .appendAll(notice.map(
                        line -> DialogFrame.wrapped(line, WIDTH, ERROR_LINES).addClass(Theme.Classes.NOTICE)))
                .append(area)
                .appendAll(model.outcome()
                        .error()
                        .filter(error -> !comment.sending())
                        .map(error -> DialogFrame.wrapped(ErrorText.of(error), WIDTH, ERROR_LINES)
                                .addClass(Theme.Classes.ERROR)));
        return new HintedDialog(
                DialogFrame.of(title, WIDTH, content.toJavaArray(Element[]::new)),
                Messages.get(comment.sending() ? hints.sending() : hints.idle()));
    }

    private static Element about(String line) {
        return text(line).addClass(Theme.Classes.DIM);
    }

    /**
     * Goes back on Escape, sends on {@link #CONFIRM_KEY} and opens the comment in the editor on {@link #EDITOR_KEY},
     * from the text area or from a field above it. The dialog around them only gets keys after the global handlers,
     * and those take Escape and Ctrl+S too.
     */
    public EventResult keys(KeyEvent event) {
        if (event.isCancel()) {
            dispatch.accept(new ComposeMsg.Cancelled());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            // A blank comment keeps the popup open.
            dispatch.accept(new ComposeMsg.Confirmed());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(EDITOR_KEY)) {
            dispatch.accept(new ComposeMsg.EditorRequested());
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
     * The keys of the hints below the popup.
     *
     * @param idle    while it is written
     * @param sending while it is sent
     */
    public record Hints(String idle, String sending) {

        /**
         * The hints of a comment.
         */
        public static final Hints COMMENT = new Hints("dialog.comment.hint", "dialog.comment.sending");
    }
}
