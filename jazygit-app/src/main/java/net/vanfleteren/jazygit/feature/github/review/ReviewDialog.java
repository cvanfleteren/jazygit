package net.vanfleteren.jazygit.feature.github.review;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Where;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommentComposer;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a comment on the lines of the diff of a pull request's files, for a reply to one of its threads
 * (ADR 60), or for the edit of a comment of one (ADR 65), as a {@link CommentComposer}: titled with where it goes,
 * under the lines it is about, the last comment of the thread, or the comment before the one edited. The comment lives in the {@link Model}, as a {@link ReviewPopup}.
 */
public final class ReviewDialog implements PopupView {

    public static final String ID = "pull-request-review-comment";

    private static final CommentComposer.Actions ACTIONS = new CommentComposer.Actions(
            ReviewMsg.Edited::new, ReviewMsg.Confirmed::new, ReviewMsg.Cancelled::new, ReviewMsg.EditorRequested::new);

    private final CommentComposer composer;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ReviewDialog(Consumer<Msg> dispatch) {
        this.composer = new CommentComposer(ID, dispatch);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(ReviewPopup.class)
                .map(popup -> composer.render(
                        model,
                        title(popup),
                        popup.quoted(),
                        new CommentComposer.Comment(popup.text(), popup.sending()),
                        ACTIONS));
    }

    private static String title(ReviewPopup popup) {
        Where where = popup.where();
        if (where.reply()) {
            return Messages.get("dialog.review.titleReply", where.path());
        }
        // An edit says so, on the lines of its thread (ADR 65).
        String key =
                popup.target() instanceof ReviewPopup.Target.Edit ? "dialog.review.titleEdit" : "dialog.review.title";
        return where.lines()
                .map(lines -> lines.first() == lines.last()
                        ? Messages.get(key + "Line", where.path(), String.valueOf(lines.last()))
                        : Messages.get(
                                key + "Lines",
                                where.path(),
                                String.valueOf(lines.first()),
                                String.valueOf(lines.last())))
                .getOrElse(() -> Messages.get(key + "File", where.path()));
    }
}
