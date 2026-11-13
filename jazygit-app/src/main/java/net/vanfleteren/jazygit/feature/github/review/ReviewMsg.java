package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.github.GitHubError;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

/**
 * Commenting on the lines of the diff of a pull request's files, from its cursor (ADR 60): starting a review thread,
 * replying to one, resolving it, opening or closing it, editing (ADR 65) or deleting a comment of it (ADR 64), and
 * reacting to one (ADR 86).
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
     * The user asked to open or close the thread of the cursor {@code at}.
     */
    record ThreadToggled(DiffCursorAt at) implements ReviewMsg {}

    /**
     * The user asked to open the pull request in the browser, on the comment of the cursor {@code at}, if it is on one
     * (ADR 85).
     */
    record OpenAsked(DiffCursorAt at) implements ReviewMsg {}

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

    /**
     * The user asked to delete the comment of the cursor {@code at} (ADR 64).
     */
    record DeleteAsked(DiffCursorAt at) implements ReviewMsg {}

    /**
     * The user confirmed deleting the comment.
     */
    record DeleteConfirmed() implements ReviewMsg {}

    /**
     * The comment was deleted by running {@code commands}, or GitHub had it no longer.
     */
    record CommentDeleted(ReviewCmd.DeleteComment cmd, List<ExecutedCommand> commands) implements ReviewMsg {}

    /**
     * Deleting the comment failed.
     */
    record DeleteFailed(ReviewCmd.DeleteComment cmd, Failure failure) implements ReviewMsg {}

    /**
     * The user asked to react to the comment of the cursor {@code at} (ADR 86).
     */
    record ReactAsked(DiffCursorAt at) implements ReviewMsg {}

    /**
     * The user chose {@code reaction}: to give it, or to take it back when they gave it.
     */
    record ReactionChosen(Reaction reaction) implements ReviewMsg {}

    /**
     * The reaction was given or taken back by running {@code commands}.
     */
    record Reacted(ReviewCmd.React cmd, List<ExecutedCommand> commands) implements ReviewMsg {}

    /**
     * Giving or taking back the reaction failed.
     */
    record ReactFailed(ReviewCmd.React cmd, Failure failure) implements ReviewMsg {}
}
