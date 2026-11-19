package net.vanfleteren.jazygit.features.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.github.GitHubError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;

/**
 * Commenting on the lines of the diff of a pull request's files, from its cursor (ADR 60): starting a review thread,
 * replying to one, resolving it, and editing a comment of it (ADR 65). Deleting a comment is a {@link
 * DeleteCommentMsg}, reacting to one a {@link ReactMsg}, and collapsing or opening one a {@link ThreadMsg}.
 */
public sealed interface ReviewMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ReviewUpdate.update(model, this);
    }

    /**
     * The user asked to start a thread on the line, the range or the file of the cursor {@code at}.
     */
    record StartAsked(DiffCursorAt at) implements ReviewMsg {}

    /**
     * The user asked to reply to the thread of the cursor {@code at}.
     */
    record ReplyAsked(DiffCursorAt at) implements ReviewMsg {}

    /**
     * The user asked to resolve the thread of the cursor {@code at}, or to unresolve it.
     */
    record ResolveAsked(DiffCursorAt at) implements ReviewMsg {}

    /**
     * The user pressed a key the cursor is not on the right stop for, as {@code why} says.
     */
    record Refused(GitHubError.CannotReview.Why why) implements ReviewMsg {}

    /**
     * The comment was sent by running {@code commands}: a reply, or a thread started.
     */
    record Sent(ReviewCmd.Send cmd, List<ExecutedCommand> commands) implements ReviewMsg {}

    /**
     * The edit of a comment was saved by running {@code commands}, and GitHub holds it as {@code body} (ADR 65).
     */
    record CommentEdited(ReviewCmd.Send cmd, String body, List<ExecutedCommand> commands) implements ReviewMsg {}

    /**
     * Sending the comment, or saving its edit, failed.
     */
    record SendFailed(ReviewCmd.Send cmd, Failure failure) implements ReviewMsg {}

    /**
     * The thread was resolved or unresolved by running {@code commands}.
     */
    record Resolved(ReviewCmd.Resolve cmd, List<ExecutedCommand> commands) implements ReviewMsg {}

    /**
     * Resolving or unresolving the thread failed.
     */
    record ResolveFailed(ReviewCmd.Resolve cmd, Failure failure) implements ReviewMsg {}

    /**
     * The user asked to edit the comment of the cursor {@code at} (ADR 65).
     */
    record EditAsked(DiffCursorAt at) implements ReviewMsg {}
}
