package net.vanfleteren.jazygit.feature.github.comment;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Commenting on the pull request that is shown: writing the comment, in the popup or in the editor, and sending it.
 */
public sealed interface CommentMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommentUpdate.update(model, this);
    }

    /**
     * The user asked to comment on the pull request that is shown.
     */
    record Requested() implements CommentMsg {}

    /**
     * The user changed the comment to {@code text}.
     */
    record Edited(String text) implements CommentMsg {}

    /**
     * The user asked to send the comment.
     */
    record Confirmed() implements CommentMsg {}

    record Cancelled() implements CommentMsg {}

    /**
     * The user asked to write the comment in the editor.
     */
    record EditorRequested() implements CommentMsg {}

    /**
     * The editor ran {@code commands}, and saved the comment as {@code text}.
     */
    record EditorClosed(String text, List<ExecutedCommand> commands) implements CommentMsg {}

    record EditorFailed(Failure failure) implements CommentMsg {}

    /**
     * The comment was added by running {@code commands}.
     */
    record Sent(CommentCmd.AddComment cmd, List<ExecutedCommand> commands) implements CommentMsg {}

    /**
     * Adding the comment failed.
     */
    record SendFailed(CommentCmd.AddComment cmd, Failure failure) implements CommentMsg {}
}
