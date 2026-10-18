package net.vanfleteren.jazygit.feature.commit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordPopup;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.ui.LineEditInput;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a commit message: a summary and a description. Tab switches between them. Enter
 * in the summary, or Ctrl+S in either, confirms; Escape cancels. Up and Down in the summary go through the summaries of
 * earlier commits, see {@link SummaryHistory}. Terminals send Ctrl+Enter as a plain Enter, so it
 * only confirms where the terminal tells them apart. Whether it is open
 * lives in the {@link Model}; the text being typed lives in the input widgets.
 *
 * <p>There are two flavours: {@link #commit} asks for the message of a new commit, {@link #reword}
 * for a new message of any commit, starting out with its current one.
 */
public final class CommitDialog implements PopupView {

    /**
     * The id of the summary field of the commit popup, which gets the focus when the popup opens.
     */
    public static final String ID = "commit-summary";

    public static final String DESCRIPTION_ID = "commit-description";
    public static final String REWORD_ID = "reword-summary";
    public static final String REWORD_DESCRIPTION_ID = "reword-description";

    // With Ctrl held, opens the message in the editor.
    private static final char EDITOR_KEY = 'o';
    // With Ctrl held, confirms from either field; Enter adds a line to the description.
    private static final char CONFIRM_KEY = 's';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int DESCRIPTION_HEIGHT = 10;

    private final TextInputState summary = new TextInputState();
    private final TextAreaState description = new TextAreaState();
    private SummaryHistory history = SummaryHistory.of(List.empty());
    private final String summaryId;
    private final String descriptionId;
    // The message the popup shows when it opens, or comes back from the editor, if not an empty one.
    private final Function<Model, Option<CommitMessage>> current;
    private final Predicate<Model> open;
    private final Function<CommitMessage, Msg> confirmed;
    private final Supplier<Msg> cancelled;
    private final Consumer<Msg> dispatch;
    private Option<Popup> shownFor = Option.none();

    private CommitDialog(
            String summaryId,
            String descriptionId,
            Predicate<Model> open,
            Function<Model, Option<CommitMessage>> current,
            Function<CommitMessage, Msg> confirmed,
            Supplier<Msg> cancelled,
            Consumer<Msg> dispatch) {
        this.summaryId = summaryId;
        this.descriptionId = descriptionId;
        this.open = open;
        this.current = current;
        this.confirmed = confirmed;
        this.cancelled = cancelled;
        this.dispatch = dispatch;
    }

    /**
     * The popup asking for the message of a new commit.
     *
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommitDialog(Consumer<Msg> dispatch) {
        this(
                ID,
                DESCRIPTION_ID,
                model -> model.popup(CommitPopup.class).isDefined(),
                model -> model.popup(CommitPopup.class).flatMap(CommitPopup::draft),
                CommitMsg.Confirmed::new,
                CommitMsg.Cancelled::new,
                dispatch);
    }

    /**
     * The popup asking for a new message of the last commit.
     *
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static CommitDialog reword(Consumer<Msg> dispatch) {
        return new CommitDialog(
                REWORD_ID,
                REWORD_DESCRIPTION_ID,
                model -> model.popup(RewordPopup.class).isDefined(),
                model -> model.popup(RewordPopup.class).map(RewordPopup::message),
                RewordMsg.Confirmed::new,
                RewordMsg.Cancelled::new,
                dispatch);
    }

    /**
     * The ids of the fields of the popup; the first one gets the focus when it opens.
     */
    @Override
    public List<String> ids() {
        return List.of(summaryId, descriptionId);
    }

    /**
     * The popup, while the model asks for a commit message.
     */
    @Override
    public Option<Element> render(Model model) {
        Option<Popup> popup = model.popup().filter(p -> open.test(model));
        boolean opened = popup.isDefined() && !shownFor.equals(popup);
        shownFor = popup;
        if (popup.isEmpty()) {
            return Option.none();
        }
        if (opened) {
            history = SummaryHistory.of(model.repo().commits().getOrElse(List.empty()));
            current.apply(model).forEach(message -> {
                summary.setText(message.summary());
                summary.moveCursorToEnd();
                description.setText(message.description());
                description.moveCursorToEnd();
            });
        }
        return Option.of(dialog(
                        new LineEditInput(
                                textInput(summary)
                                        .title(Messages.get("dialog.commit.summary.title"))
                                        .placeholder(Messages.get("dialog.commit.summary.placeholder"))
                                        .length(INPUT_HEIGHT)
                                        .onSubmit(this::confirm)
                                        .onKeyEvent(this::summaryKeys),
                                summary,
                                summaryId),
                        new SubmittableTextArea(
                                        textArea(description)
                                                .title(Messages.get("dialog.commit.description.title"))
                                                .placeholder(Messages.get("dialog.commit.description.placeholder")),
                                        description,
                                        descriptionId,
                                        this::confirm)
                                .rows(DESCRIPTION_HEIGHT)
                                .bottomTitle(Messages.get("dialog.commit.hint"))
                                .onKeyEvent(this::keys))
                .borderType(BorderType.NONE)
                .width(WIDTH)
                .onCancel(this::cancel));
    }

    private void confirm() {
        String message = summary.text();
        String details = description.text();
        // A blank summary keeps the popup open, see CommitUpdate and RewordUpdate.
        if (!message.isBlank()) {
            clear();
        }
        dispatch.accept(confirmed.apply(new CommitMessage(message, details)));
    }

    /**
     * Cancels on Escape in a field, confirms on {@link #CONFIRM_KEY} and opens the message in the editor on
     * {@link #EDITOR_KEY}. The dialog around the fields only gets keys after the global handlers, and those take
     * Escape and Ctrl+S too.
     */
    private EventResult keys(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            cancel();
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            confirm();
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(EDITOR_KEY)) {
            dispatch.accept(new MessageEditorMsg.Requested(new CommitMessage(summary.text(), description.text())));
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Shows an older summary on Up and a newer one on Down, besides the {@link #keys} of both fields.
     */
    private EventResult summaryKeys(KeyEvent event) {
        if (event.code() != KeyCode.UP && event.code() != KeyCode.DOWN) {
            return keys(event);
        }
        // At either end the key does nothing, and the summary stays as it is.
        (event.code() == KeyCode.UP ? history.older(summary.text()) : history.newer()).forEach(next -> {
            history = next;
            summary.setText(next.shown());
            summary.moveCursorToEnd();
        });
        return EventResult.HANDLED;
    }

    private void cancel() {
        clear();
        dispatch.accept(cancelled.get());
    }

    private void clear() {
        summary.clear();
        description.clear();
    }
}
