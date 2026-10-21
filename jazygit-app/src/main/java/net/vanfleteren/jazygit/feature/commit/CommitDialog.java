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
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.revert.RevertMsg;
import net.vanfleteren.jazygit.feature.commit.revert.RevertPopup;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordPopup;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.LineEditInput;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a commit message: a summary and a description. Tab switches between them. Enter
 * in the summary, or Ctrl+S in either, confirms; Escape cancels. Up and Down in the summary go through the summaries of
 * earlier commits, see {@link SummaryHistory}. Terminals send Ctrl+Enter as a plain Enter, so it
 * only confirms where the terminal tells them apart. Whether it is open, and the message as far as it was typed,
 * live in the {@link Model}, as a {@link MessagePopup}: the fields show its message, and send a
 * {@link MessageEdited} for every change. Only the carets, and where Up and Down are in the earlier summaries, are
 * kept here.
 *
 * <p>There are three flavours: {@link #commit} asks for the message of a new commit, {@link #reword}
 * for a new message of any commit, starting out with its current one, and {@link #revert} for the message of the
 * revert of a commit, starting out with the one git would write.
 */
public final class CommitDialog implements PopupView {

    /**
     * The id of the summary field of the commit popup, which gets the focus when the popup opens.
     */
    public static final String ID = "commit-summary";

    public static final String DESCRIPTION_ID = "commit-description";
    public static final String REWORD_ID = "reword-summary";
    public static final String REWORD_DESCRIPTION_ID = "reword-description";
    public static final String REVERT_ID = "revert-summary";
    public static final String REVERT_DESCRIPTION_ID = "revert-description";

    // With Ctrl held, opens the message in the editor.
    private static final char EDITOR_KEY = 'o';
    // With Ctrl held, confirms from either field; Enter adds a line to the description.
    private static final char CONFIRM_KEY = 's';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int DESCRIPTION_HEIGHT = 10;

    private final TextInputState summary = new TextInputState();
    private final TextAreaState description = new TextAreaState();
    // Where Up and Down are in the summaries of earlier commits, while the popup is open.
    private Option<SummaryHistory> history = Option.none();
    private final String summaryId;
    private final String descriptionId;
    private final Class<? extends MessagePopup> popupType;
    private final Function<Model, String> summaryTitle;
    private final Supplier<Msg> confirmed;
    private final Supplier<Msg> cancelled;
    private final Consumer<Msg> dispatch;

    private CommitDialog(
            String summaryId,
            String descriptionId,
            Class<? extends MessagePopup> popupType,
            Function<Model, String> summaryTitle,
            Supplier<Msg> confirmed,
            Supplier<Msg> cancelled,
            Consumer<Msg> dispatch) {
        this.summaryId = summaryId;
        this.descriptionId = descriptionId;
        this.popupType = popupType;
        this.summaryTitle = summaryTitle;
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
                CommitPopup.class,
                CommitDialog::summaryTitle,
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
                RewordPopup.class,
                CommitDialog::summaryTitle,
                RewordMsg.Confirmed::new,
                RewordMsg.Cancelled::new,
                dispatch);
    }

    /**
     * The popup asking for the message of the revert of a commit; its title says when that is a merge, reverted
     * against its first parent.
     *
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static CommitDialog revert(Consumer<Msg> dispatch) {
        return new CommitDialog(
                REVERT_ID,
                REVERT_DESCRIPTION_ID,
                RevertPopup.class,
                model -> model.popup(RevertPopup.class)
                        .map(RevertPopup::commit)
                        .map(commit -> commit.isMerge()
                                ? Messages.get("dialog.revert.mergeTitle", commit.shortSha())
                                : Messages.get("dialog.revert.title", commit.shortSha()))
                        .getOrElse(() -> summaryTitle(model)),
                RevertMsg.Confirmed::new,
                RevertMsg.Cancelled::new,
                dispatch);
    }

    private static String summaryTitle(Model model) {
        return Messages.get("dialog.commit.summary.title");
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
        Option<? extends MessagePopup> popup = model.popup(popupType);
        if (popup.isEmpty()) {
            history = Option.none();
            return Option.none();
        }
        if (history.isEmpty()) {
            history = Option.of(SummaryHistory.of(model.repo().commits().getOrElse(List.empty())));
        }
        // The fields show what the model has: what was typed in them, or what came back from the editor.
        CommitMessage message = popup.get().message();
        if (!summary.text().equals(message.summary())) {
            summary.setText(message.summary());
            summary.moveCursorToEnd();
        }
        if (!description.text().equals(message.description())) {
            description.setText(message.description());
            description.moveCursorToEnd();
        }
        return Option.of(dialog(
                        new LineEditInput(
                                        textInput(summary)
                                                .title(summaryTitle.apply(model))
                                                .placeholder(Messages.get("dialog.commit.summary.placeholder"))
                                                .length(INPUT_HEIGHT)
                                                .onSubmit(this::confirm)
                                                .onKeyEvent(this::summaryKeys),
                                        summary,
                                        summaryId)
                                .onChange(text -> edited()),
                        new SubmittableTextArea(
                                        textArea(description)
                                                .title(Messages.get("dialog.commit.description.title"))
                                                .placeholder(Messages.get("dialog.commit.description.placeholder")),
                                        description,
                                        descriptionId,
                                        this::confirm)
                                .onChange(text -> edited())
                                .rows(DESCRIPTION_HEIGHT)
                                .bottomTitle(Messages.get("dialog.commit.hint"))
                                .onKeyEvent(this::keys))
                .borderType(BorderType.NONE)
                .width(WIDTH)
                .onCancel(this::cancel));
    }

    /**
     * Tells the model what the message is now. The text comes from the fields, which hold every key typed so far,
     * also those typed since the model was last rendered.
     */
    private void edited() {
        dispatch.accept(new MessageEdited(new CommitMessage(summary.text(), description.text())));
    }

    private void confirm() {
        // A blank summary keeps the popup open, see CommitUpdate and RewordUpdate.
        dispatch.accept(confirmed.get());
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
            dispatch.accept(new MessageEditorMsg.Requested());
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
        history.flatMap(at -> event.code() == KeyCode.UP ? at.older(summary.text()) : at.newer())
                .forEach(next -> {
                    history = Option.of(next);
                    summary.setText(next.shown());
                    summary.moveCursorToEnd();
                    edited();
                });
        return EventResult.HANDLED;
    }

    private void cancel() {
        dispatch.accept(cancelled.get());
    }
}
