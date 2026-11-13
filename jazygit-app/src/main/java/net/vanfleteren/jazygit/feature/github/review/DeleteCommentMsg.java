package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

/**
 * Deleting a comment of a review thread of the diff of a pull request's files, from its cursor, after asking (ADR 64).
 */
public sealed interface DeleteCommentMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DeleteCommentUpdate.update(model, this);
    }

    /**
     * The user asked to delete the comment of the cursor {@code at} (ADR 64).
     */
    record DeleteAsked(DiffCursorAt at) implements DeleteCommentMsg {}

    /**
     * The user confirmed deleting the comment.
     */
    record DeleteConfirmed() implements DeleteCommentMsg {}

    /**
     * The comment was deleted by running {@code commands}, or GitHub had it no longer.
     */
    record CommentDeleted(DeleteCommentCmd.DeleteComment cmd, List<ExecutedCommand> commands)
            implements DeleteCommentMsg {}

    /**
     * Deleting the comment failed.
     */
    record DeleteFailed(DeleteCommentCmd.DeleteComment cmd, Failure failure) implements DeleteCommentMsg {}
}
