package net.vanfleteren.jazygit.feature.github.comment;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommentComposer;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup asking for a comment on the pull request that is shown, as a {@link CommentComposer}: Enter adds a line,
 * Ctrl+S sends it, Ctrl+O opens it in the editor and Escape goes back to the pull request. While it is being sent, it
 * says so; when sending failed, it says why, under the comment. The comment lives in the {@link Model}, as a
 * {@link CommentPopup}.
 */
public final class CommentDialog implements PopupView {

    public static final String ID = "pull-request-comment";

    private static final CommentComposer.Actions ACTIONS = new CommentComposer.Actions(
            CommentMsg.Edited::new,
            CommentMsg.Confirmed::new,
            CommentMsg.Cancelled::new,
            CommentMsg.EditorRequested::new);

    private final CommentComposer composer;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public CommentDialog(Consumer<Msg> dispatch) {
        this.composer = new CommentComposer(ID, dispatch);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(CommentPopup.class)
                .map(popup -> composer.render(
                        model,
                        Messages.get("dialog.comment.title", String.valueOf(popup.number())),
                        List.empty(),
                        new CommentComposer.Comment(popup.text(), popup.sending()),
                        ACTIONS));
    }
}
