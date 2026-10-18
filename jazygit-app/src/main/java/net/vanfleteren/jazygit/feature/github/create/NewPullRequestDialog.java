package net.vanfleteren.jazygit.feature.github.create;

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
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.LineEditInput;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking what a new pull request says: its title, which starts out as the subject of the oldest commit of
 * the branch, its body, which starts out empty, and whether it is a draft. Tab goes from one field to the next; space
 * or Enter on the draft box ticks it. Enter in the title, or Ctrl+S anywhere, creates the pull request; Escape
 * cancels. Whether it is open lives in the {@link Model}; what is typed and ticked lives in the widgets.
 */
public final class NewPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "new-pull-request-title";

    public static final String BODY_ID = "new-pull-request-body";
    public static final String DRAFT_ID = "new-pull-request-draft";

    /**
     * The class of the draft box, which the themes reverse when it has the focus.
     */
    static final String CHECKBOX = "checkbox";

    // With Ctrl held, creates the pull request from any field; Enter adds a line to the body.
    private static final char CONFIRM_KEY = 's';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int BODY_HEIGHT = 10;
    private static final int BOX_WIDTH = 3;

    private final TextInputState title = new TextInputState();
    private final TextAreaState body = new TextAreaState();
    private final Consumer<Msg> dispatch;
    private boolean draft;
    private Option<NewPullRequestPopup> shownFor = Option.none();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public NewPullRequestDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * The ids of the fields of the popup; the title gets the focus when it opens.
     */
    @Override
    public List<String> ids() {
        return List.of(ID, BODY_ID, DRAFT_ID);
    }

    /**
     * The popup, while the model asks what a new pull request says.
     */
    @Override
    public Option<Element> render(Model model) {
        Option<NewPullRequestPopup> popup = model.popup(NewPullRequestPopup.class);
        boolean opened = popup.isDefined() && !shownFor.equals(popup);
        shownFor = popup;
        return popup.map(open -> {
            if (opened) {
                title.setText(open.title());
                title.moveCursorToEnd();
                body.clear();
                draft = false;
            }
            return new HintedDialog(
                    DialogFrame.of(
                            Messages.get(
                                    "dialog.newPullRequest.title",
                                    open.branch().value(),
                                    open.base().value()),
                            WIDTH,
                            new LineEditInput(
                                    textInput(title)
                                            .title(Messages.get("dialog.newPullRequest.titleField"))
                                            .placeholder(Messages.get("dialog.newPullRequest.titlePlaceholder"))
                                            .length(INPUT_HEIGHT)
                                            .onSubmit(this::confirm)
                                            .onKeyEvent(this::keys),
                                    title,
                                    ID),
                            new SubmittableTextArea(
                                            textArea(body)
                                                    .title(Messages.get("dialog.newPullRequest.body"))
                                                    .placeholder(Messages.get("dialog.newPullRequest.bodyPlaceholder")),
                                            body,
                                            BODY_ID,
                                            this::confirm)
                                    .rows(BODY_HEIGHT)
                                    .onKeyEvent(this::keys),
                            // The box itself takes the focus, which the themes show by reversing it, as a cursor.
                            panel(row(
                                            text(box(draft))
                                                    .id(DRAFT_ID)
                                                    .addClass(CHECKBOX)
                                                    .focusable()
                                                    .length(BOX_WIDTH)
                                                    .onKeyEvent(this::draftKeys),
                                            text(" " + Messages.get("dialog.newPullRequest.draft"))))
                                    .length(INPUT_HEIGHT)),
                    Messages.get("dialog.newPullRequest.hint"));
        });
    }

    /**
     * The draft box, ticked or not.
     */
    static String box(boolean ticked) {
        return ticked ? "[x]" : "[ ]";
    }

    /**
     * Ticks or unticks the draft box on space or Enter; the other keys are those of every field.
     */
    private EventResult draftKeys(KeyEvent event) {
        if (event.code() == KeyCode.ENTER || (event.isChar(' ') && !event.hasCtrl() && !event.hasAlt())) {
            draft = !draft;
            return EventResult.HANDLED;
        }
        return keys(event);
    }

    /**
     * Cancels on Escape and creates the pull request on {@link #CONFIRM_KEY}. The dialog around the fields only
     * gets keys after the global handlers, and those take Escape and Ctrl+S too.
     */
    private EventResult keys(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new CreatePullRequestMsg.Cancelled());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            confirm();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void confirm() {
        // A blank title keeps the popup open, see CreatePullRequestUpdate.
        dispatch.accept(new CreatePullRequestMsg.Confirmed(new NewPullRequest(title.text(), body.text(), draft)));
    }
}
