package net.vanfleteren.jazygit.feature.github.edit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.github.create.NewPullRequestDialog;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BasesField;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.LineEditInput;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup editing the pull request that is shown (ADR 59), laid out and keyed as the one that creates one: the list of
 * branches it can merge into, its title, its body, and whether it is a draft. Tab goes from one part to the next; the
 * focus starts in the title. A base or draft box that cannot be changed is shown dimmed, and Tab goes past it. Enter
 * in the title, or Ctrl+S anywhere, saves what was changed; Ctrl+O opens the body in the editor; Escape clears the
 * filter of the bases, or else goes back to the pull request. While it is being saved, it says so; when saving
 * failed, it says why, under the fields. What is picked, typed and ticked lives in the {@link Model}, as an
 * {@link EditPullRequestPopup}, which every change is sent to; the fields only keep where their cursor is.
 */
public final class EditPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "edit-pull-request-title";

    public static final String BASES_ID = "edit-pull-request-bases";
    public static final String BODY_ID = "edit-pull-request-body";
    public static final String DRAFT_ID = "edit-pull-request-draft";

    // With Ctrl held, saves from any field; Enter adds a line to the body.
    private static final char CONFIRM_KEY = 's';
    // With Ctrl held, opens the body in the editor.
    private static final char EDITOR_KEY = 'o';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int BODY_HEIGHT = 10;
    private static final int BOX_WIDTH = 3;
    private static final int ERROR_LINES = 3;

    private final TextInputState title = new TextInputState();
    private final TextAreaState body = new TextAreaState();
    private final BasesField<RemoteBranch> bases;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public EditPullRequestDialog(Consumer<Msg> dispatch) {
        this.bases = new BasesField<>(BASES_ID, base -> base.branch().value(), dispatch);
        this.dispatch = dispatch;
    }

    /**
     * The ids of the parts of the popup; the title gets the focus when it opens.
     */
    @Override
    public List<String> ids() {
        return List.of(ID, BASES_ID, BODY_ID, DRAFT_ID);
    }

    /**
     * The popup, while the model edits a pull request.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(EditPullRequestPopup.class).map(popup -> {
            PullRequestFields fields = popup.fields();
            // The fields show what the model has: what was typed in them, or what came back from the editor.
            if (!title.text().equals(fields.title())) {
                title.setText(fields.title());
                title.moveCursorToEnd();
            }
            if (!body.text().equals(fields.body())) {
                body.setText(fields.body());
            }
            // The error of saving it, or of the editor; a new try clears it.
            List<Element> content = List.of(
                            base(model, popup),
                            new LineEditInput(
                                            textInput(title)
                                                    .title(Messages.get("dialog.newPullRequest.titleField"))
                                                    .placeholder(Messages.get("dialog.newPullRequest.titlePlaceholder"))
                                                    .length(INPUT_HEIGHT)
                                                    .onSubmit(this::confirm)
                                                    .onKeyEvent(this::keys),
                                            title,
                                            ID)
                                    .onChange(text -> edited(fields.draft())),
                            new SubmittableTextArea(
                                            textArea(body)
                                                    .title(Messages.get("dialog.newPullRequest.body"))
                                                    .placeholder(
                                                            Messages.get("dialog.editPullRequest.bodyPlaceholder")),
                                            body,
                                            BODY_ID,
                                            this::confirm)
                                    .onChange(text -> edited(fields.draft()))
                                    .rows(BODY_HEIGHT)
                                    .onKeyEvent(this::keys),
                            draft(popup))
                    .appendAll(model.outcome()
                            .error()
                            .filter(error -> !popup.saving())
                            .map(error -> DialogFrame.wrapped(ErrorText.of(error), WIDTH, ERROR_LINES)
                                    .addClass(Theme.Classes.ERROR)));
            return new HintedDialog(
                    DialogFrame.of(
                            Messages.get(
                                    "dialog.editPullRequest.title",
                                    String.valueOf(popup.number()),
                                    popup.pullRequestPopup().branch().value()),
                            WIDTH,
                            content.toJavaArray(Element[]::new)),
                    Messages.get(popup.saving() ? "dialog.editPullRequest.saving" : "dialog.editPullRequest.hint"));
        });
    }

    /**
     * The list of the bases, or the base dimmed, with why, when it cannot be changed: a pull request stacked on
     * another merges into the branch of that one, and a closed or merged one keeps its base.
     */
    private Element base(Model model, EditPullRequestPopup popup) {
        RemoteBranch base = popup.fields().base();
        return popup.baseChangeable()
                ? bases.render(popup.bases(), base, model.listCursor(), EditPullRequestMsg.BaseMoved::new, this::keys)
                : bases.fixed(
                        base,
                        Messages.get(
                                popup.pullRequestPopup().open()
                                        ? "dialog.editPullRequest.baseStacked"
                                        : "dialog.editPullRequest.baseClosed"));
    }

    /**
     * The draft box, which takes the focus, as a cursor that the themes show by reversing it; dimmed and without the
     * focus when it cannot be changed.
     */
    private Element draft(EditPullRequestPopup popup) {
        boolean ticked = popup.fields().draft();
        Element box = popup.draftChangeable()
                ? text(NewPullRequestDialog.box(ticked))
                        .id(DRAFT_ID)
                        .addClass(NewPullRequestDialog.CHECKBOX)
                        .focusable()
                        .length(BOX_WIDTH)
                        .onKeyEvent(event -> draftKeys(event, ticked))
                : text(NewPullRequestDialog.box(ticked))
                        .addClass(Theme.Classes.DIM)
                        .length(BOX_WIDTH);
        Element label = popup.draftChangeable()
                ? text(" " + Messages.get("dialog.newPullRequest.draft"))
                : text(" " + Messages.get("dialog.newPullRequest.draft")).addClass(Theme.Classes.DIM);
        return panel(row(box, label)).length(INPUT_HEIGHT);
    }

    /**
     * Ticks or unticks the draft box on space or Enter; the other keys are those of every field.
     */
    private EventResult draftKeys(KeyEvent event, boolean draft) {
        if (event.code() == KeyCode.ENTER || (event.isChar(' ') && !event.hasCtrl() && !event.hasAlt())) {
            edited(!draft);
            return EventResult.HANDLED;
        }
        return keys(event);
    }

    /**
     * Goes back on Escape, saves on {@link #CONFIRM_KEY} and opens the body in the editor on {@link #EDITOR_KEY}.
     * The dialog around the fields only gets keys after the global handlers, and those take Escape and Ctrl+S too.
     */
    private EventResult keys(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new EditPullRequestMsg.Cancelled());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            confirm();
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(EDITOR_KEY)) {
            dispatch.accept(new EditPullRequestMsg.EditorRequested());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Tells the model what the pull request says now. The text comes from the fields, which hold every key typed so
     * far, also those typed since the model was last rendered.
     */
    private void edited(boolean draft) {
        dispatch.accept(new EditPullRequestMsg.Edited(title.text(), body.text(), draft));
    }

    private void confirm() {
        // A blank title keeps the popup open, see EditPullRequestUpdate.
        dispatch.accept(new EditPullRequestMsg.Confirmed());
    }
}
