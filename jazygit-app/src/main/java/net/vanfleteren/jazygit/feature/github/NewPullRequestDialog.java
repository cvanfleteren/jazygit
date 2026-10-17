package net.vanfleteren.jazygit.feature.github;

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
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking what a new pull request says: whether it is a draft, its title, which starts out as the subject of
 * the oldest commit of the branch, and its body, which starts out empty. Tab goes from one field to the next; space
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
     * The class of the draft box, which the themes give a border in the accent colour when it has the focus.
     */
    static final String CHECKBOX = "checkbox";

    // With Ctrl held, creates the pull request from any field; Enter adds a line to the body.
    private static final char CONFIRM_KEY = 's';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int BODY_HEIGHT = 10;

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
     * The ids of the fields of the popup, in the order they are shown.
     */
    @Override
    public List<String> ids() {
        return List.of(DRAFT_ID, ID, BODY_ID);
    }

    /**
     * The title gets the focus when the popup opens.
     */
    @Override
    public Option<String> initialFocus() {
        return Option.of(ID);
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
            return DialogFrame.of(
                    Messages.get(
                            "dialog.newPullRequest.title",
                            open.branch().value(),
                            open.base().value()),
                    WIDTH,
                    panel(text(checkbox(draft)))
                            .id(DRAFT_ID)
                            .addClass(CHECKBOX)
                            .focusable()
                            .length(INPUT_HEIGHT)
                            .onKeyEvent(this::draftKeys),
                    textInput(title)
                            .id(ID)
                            .title(Messages.get("dialog.newPullRequest.titleField"))
                            .placeholder(Messages.get("dialog.newPullRequest.titlePlaceholder"))
                            .length(INPUT_HEIGHT)
                            .onSubmit(this::confirm)
                            .onKeyEvent(this::keys),
                    new SubmittableTextArea(
                                    textArea(body)
                                            .title(Messages.get("dialog.newPullRequest.body"))
                                            .placeholder(Messages.get("dialog.newPullRequest.bodyPlaceholder")),
                                    BODY_ID,
                                    this::confirm)
                            .rows(BODY_HEIGHT)
                            .bottomTitle(Messages.get("dialog.newPullRequest.hint"))
                            .onKeyEvent(this::keys));
        });
    }

    /**
     * The draft box, ticked or not, with its label.
     */
    static String checkbox(boolean ticked) {
        return (ticked ? "[x] " : "[ ] ") + Messages.get("dialog.newPullRequest.draft");
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
            dispatch.accept(new PullRequestMsg.CreateCancelled());
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            confirm();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void confirm() {
        // A blank title keeps the popup open, see PullRequestUpdate.
        dispatch.accept(new PullRequestMsg.CreateConfirmed(new NewPullRequest(title.text(), body.text(), draft)));
    }
}
