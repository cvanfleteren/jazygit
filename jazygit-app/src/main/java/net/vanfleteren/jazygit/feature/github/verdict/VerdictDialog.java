package net.vanfleteren.jazygit.feature.github.verdict;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommentComposer;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a review of a pull request (ADR 100), as a {@link CommentComposer} under a choice of what it says,
 * in a box of its own: {@code (•) Approve  ( ) Request changes}. The text is in a box too, whose title says whether
 * the verdict chosen needs it. The choices are one stop of the focus, as a group of radio buttons is: their box takes
 * it, and shows it by its border, as the box of the text does, and the arrow keys choose. The focus starts there, on approve; Tab goes on to the text. Ctrl+S sends it, Ctrl+O opens the text in
 * the editor and Escape goes back, from the choices as from the text. A draft says so, above the text. The review lives in the {@link Model}, as a {@link VerdictPopup}.
 */
public final class VerdictDialog implements PopupView {

    public static final String ID = "pull-request-verdict";
    // The box of the choices, one stop of the focus.
    public static final String CHOICES_ID = "pull-request-verdict-choices";

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
        return List.of(CHOICES_ID, ID);
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
        // The box takes the focus, so its border shows it as that of the text does.
        return panel(row(
                        mark(Verdict.APPROVE, chosen),
                        text(approve).length(approve.length()),
                        text("").length(GAP),
                        mark(Verdict.REQUEST_CHANGES, chosen),
                        text(changes).length(changes.length())))
                .title(Messages.get("dialog.verdict.choices"))
                .id(CHOICES_ID)
                .addClass(Theme.Classes.CHOICES)
                .focusable()
                .onKeyEvent(this::keys)
                .length(BOX_HEIGHT);
    }

    /**
     * The mark of the choice of {@code verdict}, filled when it is {@code chosen}.
     */
    private static Element mark(Verdict verdict, Verdict chosen) {
        return text(verdict == chosen ? "(•)" : "( )").length(MARK_WIDTH);
    }

    /**
     * Chooses approving on Left or Up and asking for changes on Right or Down, as the arrows go through a group of
     * radio buttons; the other keys are those of the text.
     */
    private EventResult keys(KeyEvent event) {
        return switch (event.code()) {
            case LEFT, UP -> chosen(Verdict.APPROVE);
            case RIGHT, DOWN -> chosen(Verdict.REQUEST_CHANGES);
            default -> composer.keys(event, ACTIONS);
        };
    }

    private EventResult chosen(Verdict verdict) {
        dispatch.accept(new VerdictMsg.Chosen(verdict));
        return EventResult.HANDLED;
    }
}
