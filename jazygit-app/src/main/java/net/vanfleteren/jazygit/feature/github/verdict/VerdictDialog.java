package net.vanfleteren.jazygit.feature.github.verdict;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommentComposer;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a review of a pull request, which approves it or asks for changes (ADR 99), as a
 * {@link CommentComposer}: its title says which, and Ctrl+T switches; Enter adds a line, Ctrl+S sends it, Ctrl+O opens it
 * in the editor and Escape goes back. A draft says so, above the text. The review lives in the {@link Model}, as a
 * {@link VerdictPopup}.
 */
public final class VerdictDialog implements PopupView {

    public static final String ID = "pull-request-verdict";

    private static final CommentComposer.Actions ACTIONS = new CommentComposer.Actions(
            VerdictMsg.Edited::new,
            VerdictMsg.Confirmed::new,
            VerdictMsg.Cancelled::new,
            VerdictMsg.EditorRequested::new,
            Option.of(VerdictMsg.Switched::new));

    private static final CommentComposer.Hints HINTS =
            new CommentComposer.Hints("dialog.verdict.hint", "dialog.verdict.sending");

    private final CommentComposer composer;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public VerdictDialog(Consumer<Msg> dispatch) {
        this.composer = new CommentComposer(ID, dispatch);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(VerdictPopup.class)
                .map(popup -> composer.render(
                        model,
                        title(popup),
                        List.empty(),
                        Option.when(popup.draft(), () -> Messages.get("dialog.verdict.draft")),
                        new CommentComposer.Comment(popup.text(), popup.sending()),
                        ACTIONS,
                        HINTS));
    }

    private static String title(VerdictPopup popup) {
        return Messages.get(
                popup.draft() ? "dialog.verdict.titleDraft" : "dialog.verdict.title",
                String.valueOf(popup.number()),
                Messages.get(
                        popup.verdict() == Verdict.APPROVE
                                ? "dialog.verdict.approve"
                                : "dialog.verdict.requestChanges"));
    }
}
