package net.vanfleteren.jazygit.feature.github.verdict;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.github.create.NewPullRequestDialog;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommentComposer;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a review of a pull request (ADR 99), as a {@link CommentComposer} under a choice of what it says,
 * in a box of its own: {@code (•) Approve  ( ) Request changes}. The text is in a box too, whose title says whether
 * the verdict chosen needs it. Each choice takes the focus, which the themes show by reversing its mark,
 * and Space or Enter chooses it; Tab goes from one to the other and on to the text, where the focus starts on approve.
 * Ctrl+S sends it, Ctrl+O opens the text in the editor and Escape goes back, from any of them. A draft says so, above
 * the text. The review lives in the {@link Model}, as a {@link VerdictPopup}.
 */
public final class VerdictDialog implements PopupView {

    public static final String ID = "pull-request-verdict";
    public static final String APPROVE_ID = "pull-request-verdict-approve";
    public static final String CHANGES_ID = "pull-request-verdict-changes";

    private static final CommentComposer.Actions ACTIONS = new CommentComposer.Actions(
            VerdictMsg.Edited::new,
            VerdictMsg.Confirmed::new,
            VerdictMsg.Cancelled::new,
            VerdictMsg.EditorRequested::new);

    private static final CommentComposer.Hints HINTS =
            new CommentComposer.Hints("dialog.verdict.hint", "dialog.verdict.sending");

    private static final int MARK_WIDTH = 3;
    // The box of the choices: their row and its borders.
    private static final int BOX_HEIGHT = 3;
    // Between the label of the first choice and the mark of the second.
    private static final int GAP = 3;

    private final CommentComposer composer;
    private final Consumer<Msg> dispatch;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public VerdictDialog(Consumer<Msg> dispatch) {
        this.composer = new CommentComposer(ID, dispatch);
        this.dispatch = dispatch;
    }

    /**
     * The choices first, so the focus starts on approving, then the text.
     */
    @Override
    public List<String> ids() {
        return List.of(APPROVE_ID, CHANGES_ID, ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(VerdictPopup.class)
                .map(popup -> composer.render(
                        model,
                        Messages.get(
                                popup.draft() ? "dialog.verdict.titleDraft" : "dialog.verdict.title",
                                String.valueOf(popup.number())),
                        List.of(choices(popup.verdict())),
                        List.empty(),
                        Option.when(popup.draft(), () -> Messages.get("dialog.verdict.draft")),
                        // Whether a text is needed, as the verdict chosen says.
                        Option.of(Messages.get(
                                popup.verdict().needsText()
                                        ? "dialog.verdict.textRequired"
                                        : "dialog.verdict.textOptional")),
                        new CommentComposer.Comment(popup.text(), popup.sending()),
                        ACTIONS,
                        HINTS));
    }

    /**
     * The choices, in a box of their own, set apart from the text, as the fields of the pull request dialogs are.
     */
    private Element choices(Verdict chosen) {
        String approve = " " + Messages.get("dialog.verdict.approve");
        String changes = " " + Messages.get("dialog.verdict.requestChanges");
        return panel(row(
                        mark(APPROVE_ID, Verdict.APPROVE, chosen),
                        text(approve).length(approve.length()),
                        text("").length(GAP),
                        mark(CHANGES_ID, Verdict.REQUEST_CHANGES, chosen),
                        text(changes).length(changes.length())))
                .title(Messages.get("dialog.verdict.choices"))
                .length(BOX_HEIGHT);
    }

    /**
     * The mark of the choice of {@code verdict}, filled when it is {@code chosen}, which takes the focus.
     */
    private Element mark(String id, Verdict verdict, Verdict chosen) {
        return text(verdict == chosen ? "(•)" : "( )")
                .id(id)
                .addClass(NewPullRequestDialog.CHECKBOX)
                .focusable()
                .length(MARK_WIDTH)
                .onKeyEvent(event -> keys(event, verdict));
    }

    /**
     * Chooses {@code verdict} on Space or Enter; the other keys are those of the text.
     */
    private EventResult keys(KeyEvent event, Verdict verdict) {
        if (event.code() == KeyCode.ENTER || (event.isChar(' ') && !event.hasCtrl() && !event.hasAlt())) {
            dispatch.accept(new VerdictMsg.Chosen(verdict));
            return EventResult.HANDLED;
        }
        return composer.keys(event, ACTIONS);
    }
}
