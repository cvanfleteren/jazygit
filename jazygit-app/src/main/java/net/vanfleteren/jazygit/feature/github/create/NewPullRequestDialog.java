package net.vanfleteren.jazygit.feature.github.create;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.LineEditInput;
import net.vanfleteren.jazygit.ui.SubmittableTextArea;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.FocusBordered;
import net.vanfleteren.jazygit.ui.widgets.FocusHighlighted;
import net.vanfleteren.jazygit.ui.widgets.HintedDialog;
import net.vanfleteren.jazygit.ui.widgets.ListFilter;
import net.vanfleteren.jazygit.ui.widgets.ListKeys;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking what a new pull request merges into and what it says: the list of branches it can merge into, its
 * title, which starts out as the subject of the oldest commit of the branch not on the base, its body, which starts
 * out empty, and whether it is a draft. Tab goes from one part to the next; the focus starts in the title. In the
 * list, the highlighted branch is the base; the arrows move it, typing filters the list, and Enter goes on to the
 * title. Space or Enter on the draft box ticks it. Enter in the title, or Ctrl+S anywhere, creates the pull request;
 * Escape clears the filter, or else cancels. What is picked, typed and ticked lives in the {@link Model}, which every
 * change is sent to; the fields only keep where their cursor is.
 */
public final class NewPullRequestDialog implements PopupView {

    /**
     * The id of the title field, which gets the focus when the popup opens.
     */
    public static final String ID = "new-pull-request-title";

    public static final String BASES_ID = "new-pull-request-bases";
    public static final String BODY_ID = "new-pull-request-body";
    public static final String DRAFT_ID = "new-pull-request-draft";

    /**
     * The class of the draft box, which the themes reverse when it has the focus.
     */
    static final String CHECKBOX = "checkbox";

    // With Ctrl held, creates the pull request from any field; Enter adds a line to the body.
    private static final char CONFIRM_KEY = 's';
    // With Ctrl held, clears the filter of the bases, as it clears a line in readline.
    private static final char CLEAR_KEY = 'u';
    private static final int WIDTH = 110;
    private static final int INPUT_HEIGHT = 3;
    private static final int BODY_HEIGHT = 10;
    private static final int BOX_WIDTH = 3;
    // The bases shown at once, past which the list scrolls; the border takes two more lines.
    private static final int BASE_ROWS = 5;

    private final TextInputState title = new TextInputState();
    private final TextAreaState body = new TextAreaState();
    private final ListElement<?> baseList = list().id(BASES_ID).focusable().highlightSymbol("");
    // Shown instead of the bases when the filter shows none: the same id, so it keeps the focus and the keys.
    private final ListElement<?> noMatches = list().id(BASES_ID)
            .focusable()
            .highlightSymbol("")
            .highlightStyle(Style.EMPTY)
            .elements(ListFilter.noMatches());
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public NewPullRequestDialog(Consumer<Msg> dispatch) {
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
        return model.popup(NewPullRequestPopup.class).map(open -> {
            NewPullRequest pullRequest = open.pullRequest();
            // The fields show what the model has: what was typed in them, or what the popup opened with.
            if (!title.text().equals(pullRequest.title())) {
                title.setText(pullRequest.title());
                title.moveCursorToEnd();
            }
            if (!body.text().equals(pullRequest.body())) {
                body.setText(pullRequest.body());
            }
            return new HintedDialog(
                    DialogFrame.of(
                            Messages.get(
                                    "dialog.newPullRequest.title", open.branch().value()),
                            WIDTH,
                            bases(Bases.of(open, model.listCursor())),
                            new LineEditInput(
                                            textInput(title)
                                                    .title(Messages.get("dialog.newPullRequest.titleField"))
                                                    .placeholder(Messages.get("dialog.newPullRequest.titlePlaceholder"))
                                                    .length(INPUT_HEIGHT)
                                                    .onSubmit(this::confirm)
                                                    .onKeyEvent(this::keys),
                                            title,
                                            ID)
                                    .onChange(text -> edited(pullRequest.draft())),
                            new SubmittableTextArea(
                                            textArea(body)
                                                    .title(Messages.get("dialog.newPullRequest.body"))
                                                    .placeholder(Messages.get("dialog.newPullRequest.bodyPlaceholder")),
                                            body,
                                            BODY_ID,
                                            this::confirm)
                                    .onChange(text -> edited(pullRequest.draft()))
                                    .rows(BODY_HEIGHT)
                                    .onKeyEvent(this::keys),
                            // The box itself takes the focus, which the themes show by reversing it, as a cursor.
                            panel(row(
                                            text(box(pullRequest.draft()))
                                                    .id(DRAFT_ID)
                                                    .addClass(CHECKBOX)
                                                    .focusable()
                                                    .length(BOX_WIDTH)
                                                    .onKeyEvent(event -> draftKeys(event, pullRequest.draft())),
                                            text(" " + Messages.get("dialog.newPullRequest.draft"))))
                                    .length(INPUT_HEIGHT)),
                    Messages.get("dialog.newPullRequest.hint"));
        });
    }

    /**
     * The list of the bases, those the filter shows, with the base highlighted, and named in its border. The base
     * stays highlighted while another field has the focus; the border shows whether the list has it.
     */
    private Element bases(Bases view) {
        ListElement<?> list = view.shown().isEmpty()
                ? noMatches
                : baseList.elements(view.shown()
                                .<StyledElement<?>>map(base -> text(base.value()))
                                .toJavaArray(StyledElement[]::new))
                        .selected(view.selected());
        list.onKeyEvent(event -> baseKeys(view, event))
                .onMouseEvent(event -> ListKeys.wheel(
                        event,
                        view.selected(),
                        view.shown().size(),
                        line -> true,
                        line -> dispatch.accept(view.at(line))));
        String base = view.popup().base().value();
        // In a panel, which the themes give the border of the fields; a list's own border is not themed.
        return new FocusBordered(
                panel(
                                view.cursor()
                                        .filter()
                                        .map(typed -> Messages.get("dialog.newPullRequest.baseFiltered", base, typed))
                                        .getOrElse(() -> Messages.get("dialog.newPullRequest.base", base)),
                                view.shown().isEmpty() ? list : new FocusHighlighted(list))
                        .length(BASE_ROWS + 2),
                BASES_ID);
    }

    /**
     * Escape clears the filter while there is one, and Enter goes on to the title; the other keys of every field
     * come next, then those that edit the filter, then those that move the highlight. The filter shows in the border
     * of the list, where there is no cursor to move, so it only takes what is typed at its end.
     */
    private EventResult baseKeys(Bases view, KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE && view.cursor().filter().isDefined()) {
            dispatch.accept(view.filtered(Option.none()));
            return EventResult.HANDLED;
        }
        if (event.code() == KeyCode.ENTER) {
            // The title is next.
            return EventResult.FOCUS_NEXT;
        }
        EventResult common = keys(event);
        if (common.isHandled()) {
            return common;
        }
        Option<String> edited = filtered(view.cursor().filter().getOrElse(""), event);
        if (edited.isDefined()) {
            dispatch.accept(view.filtered(edited.filter(typed -> !typed.isEmpty())));
            return EventResult.HANDLED;
        }
        Option<Integer> moved =
                ListKeys.move(event, view.selected(), view.shown().size(), line -> true);
        moved.filter(line -> line != view.selected()).forEach(line -> dispatch.accept(view.at(line)));
        return moved.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * The filter {@code typed} after {@code event}, if it edits it: a character typed goes at its end, Backspace
     * takes the last one off, and Ctrl+U clears it.
     */
    static Option<String> filtered(String typed, KeyEvent event) {
        if (event.code() == KeyCode.BACKSPACE) {
            return Option.of(
                    typed.isEmpty() ? typed : typed.substring(0, typed.offsetByCodePoints(typed.length(), -1)));
        }
        if (event.hasCtrl() && event.isChar(CLEAR_KEY)) {
            return Option.of("");
        }
        return Option.when(
                event.code() == KeyCode.CHAR && !event.hasCtrl() && !event.hasAlt() && event.codePoint() >= ' ',
                () -> typed + event.string());
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
        if (event.code() == KeyCode.ENTER || (event.isChar(' ') && !event.hasCtrl() && !event.hasAlt())) {
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
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(new Msg.PopupDismissed(NewPullRequestPopup.class));
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
        dispatch.accept(
                new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest(title.text(), body.text(), draft)));
    }

    private void confirm() {
        // A blank title keeps the popup open, see CreatePullRequestUpdate.
        dispatch.accept(new CreatePullRequestMsg.Confirmed());
    }

    /**
     * The bases of {@code popup} as the list shows them, with the model's cursor in it.
     *
     * @param shown the bases that contain the filter
     */
    private record Bases(NewPullRequestPopup popup, ListCursor cursor, List<BranchName> shown) {

        static Bases of(NewPullRequestPopup popup, ListCursor cursor) {
            return new Bases(popup, cursor, shown(popup, cursor.filter()));
        }

        static List<BranchName> shown(NewPullRequestPopup popup, Option<String> filter) {
            return popup.bases().filter(base -> ListFilter.matches(filter, base.value()));
        }

        /**
         * The line of the base among those shown; while the filter hides it, there is none to highlight.
         */
        int selected() {
            return Math.max(0, shown.indexOf(popup.base()));
        }

        /**
         * The move to the base on {@code line}.
         */
        CreatePullRequestMsg.BaseMoved at(int line) {
            return new CreatePullRequestMsg.BaseMoved(
                    cursor.at(line), shown.asPartialFunction().lift().apply(line));
        }

        /**
         * The move to {@code filter}: the base stays while it shows, else the first one shown is the base; while
         * none shows, the base stays too.
         */
        CreatePullRequestMsg.BaseMoved filtered(Option<String> filter) {
            List<BranchName> after = shown(popup, filter);
            int line = Math.max(0, after.indexOf(popup.base()));
            return new CreatePullRequestMsg.BaseMoved(
                    new ListCursor(Option.of(line), filter),
                    after.asPartialFunction().lift().apply(line));
        }
    }
}
