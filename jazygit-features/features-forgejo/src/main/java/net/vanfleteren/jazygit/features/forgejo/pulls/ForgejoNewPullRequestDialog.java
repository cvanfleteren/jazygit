package net.vanfleteren.jazygit.features.forgejo.pulls;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.edit.LineEditInput;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.BasesField;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.FocusBordered;
import net.vanfleteren.jazygit.kernel.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.SubmittableTextArea;

/**
 * Popup asking what a new pull request on Forgejo merges into and what it says (ADR 178), laid out as GitHub's (ADR
 * 18, 175): the list of the branches of the home's remote it can merge into, its title, which starts out as the
 * subject of the oldest commit of the branch not on the base, its body, which starts out empty, and whether it is a
 * draft. Tab goes from one part to the next; the focus starts in the title. In the list, the highlighted branch is the
 * base; the arrows move it, typing filters the list, and Enter goes on to the title. Space or Enter on the draft box
 * ticks it. Enter in the title, or Ctrl+S anywhere, creates the pull request; Escape clears the filter, or else
 * cancels. What is picked, typed and ticked lives in the {@link Model}, which every change is sent to; the fields only
 * keep where their cursor is.
 */
public final class ForgejoNewPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "forgejo-new-pull-request-title";

    public static final String BASES_ID = "forgejo-new-pull-request-bases";
    public static final String BODY_ID = "forgejo-new-pull-request-body";
    public static final String DRAFT_ID = "forgejo-new-pull-request-draft";

    /**
     * The class of the draft box, which the themes reverse when it has the focus.
     */
    public static final String CHECKBOX = "checkbox";

    // With Ctrl held, creates the pull request from any field; Enter adds a line to the body.
    private static final char CONFIRM_KEY = 's';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int BODY_HEIGHT = 10;
    private static final int BOX_WIDTH = 3;

    private final TextInputState title = new TextInputState();
    private final TextAreaState body = new TextAreaState();
    private final BasesField<BranchName> bases;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ForgejoNewPullRequestDialog(Consumer<Msg> dispatch) {
        this.bases = new BasesField<>(BASES_ID, BranchName::value, dispatch);
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
     * The popup, while the model asks what a new pull request says.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(ForgejoNewPullRequestPopup.class).map(open -> {
            // The fields show what the model has: what was typed in them, or what the popup opened with.
            if (!title.text().equals(open.title())) {
                title.setText(open.title());
                title.moveCursorToEnd();
            }
            if (!body.text().equals(open.body())) {
                body.setText(open.body());
            }
            return new HintedDialog(
                    DialogFrame.of(
                            Messages.get(
                                    "forgejo.dialog.newPullRequest.title",
                                    open.branch().value()),
                            WIDTH,
                            bases.render(
                                    open.bases(),
                                    open.base(),
                                    model.listCursor(),
                                    CreatePullRequestMsg.BaseMoved::new,
                                    this::keys),
                            new LineEditInput(
                                            textInput(title)
                                                    .title(Messages.get("forgejo.dialog.newPullRequest.titleField"))
                                                    .placeholder(Messages.get(
                                                            "forgejo.dialog.newPullRequest.titlePlaceholder"))
                                                    .length(INPUT_HEIGHT)
                                                    .onSubmit(this::confirm)
                                                    .onKeyEvent(this::keys),
                                            title,
                                            ID)
                                    .onChange(text -> edited(open.draft())),
                            new SubmittableTextArea(
                                            textArea(body)
                                                    .title(Messages.get("forgejo.dialog.newPullRequest.body"))
                                                    .placeholder(Messages.get(
                                                            "forgejo.dialog.newPullRequest.bodyPlaceholder")),
                                            body,
                                            BODY_ID,
                                            this::confirm)
                                    .onChange(text -> edited(open.draft()))
                                    .rows(BODY_HEIGHT)
                                    .onKeyEvent(this::keys),
                            // The box itself takes the focus, which the themes show by reversing it, as a cursor,
                            // and in the border, as for the other fields.
                            new FocusBordered(
                                    panel(row(List.<StyledElement<?>>of(
                                                            text(box(open.draft()))
                                                                    .id(DRAFT_ID)
                                                                    .addClass(CHECKBOX)
                                                                    .focusable()
                                                                    .length(BOX_WIDTH)
                                                                    .onKeyEvent(
                                                                            event -> draftKeys(event, open.draft())),
                                                            text(" "
                                                                    + Messages.get(
                                                                            "forgejo.dialog.newPullRequest.draft")))
                                                    .toJavaArray(StyledElement[]::new)))
                                            .length(INPUT_HEIGHT),
                                    DRAFT_ID)),
                    Messages.get("forgejo.dialog.newPullRequest.hint"));
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
    private EventResult draftKeys(KeyEvent event, boolean draft) {
        if (event.isConfirm() || KeyTrigger.ch(' ').matches(event)) {
            edited(!draft);
            return EventResult.HANDLED;
        }
        return keys(event);
    }

    /**
     * Cancels on Escape and creates the pull request on {@link #CONFIRM_KEY}. The dialog around the fields only
     * gets keys after the global handlers, and those take Escape and Ctrl+S too.
     */
    private EventResult keys(KeyEvent event) {
        if (event.isCancel()) {
            dispatch.accept(new PopupMsg.Dismissed(ForgejoNewPullRequestPopup.class));
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(CONFIRM_KEY)) {
            confirm();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Tells the model what the pull request says now. The text comes from the fields, which hold every key typed so
     * far, also those typed since the model was last rendered.
     */
    private void edited(boolean draft) {
        dispatch.accept(new ForgejoCreatePullRequestMsg.Edited(title.text(), body.text(), draft));
    }

    private void confirm() {
        // A blank title keeps the popup open, see ForgejoCreatePullRequestUpdate.
        dispatch.accept(new ForgejoCreatePullRequestMsg.Confirmed());
    }
}
