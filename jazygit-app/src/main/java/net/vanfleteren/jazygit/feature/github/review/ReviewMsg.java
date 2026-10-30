package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Commenting on the lines of the diff of a pull request's files, from its cursor (ADR 60): starting a review thread,
 * replying to one, resolving it, and opening or closing it.
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
     * The user pressed a key the cursor is not on the right stop for, as {@code why} says.
     */
    record Refused(AppError.CannotReview.Why why) implements ReviewMsg {}

    /**
     * The user changed the comment to {@code text}.
     */
    record Edited(String text) implements ReviewMsg {}

    /**
     * The user asked to send the comment.
     */
    record Confirmed() implements ReviewMsg {}

    record Cancelled() implements ReviewMsg {}

    /**
     * The user asked to write the comment in the editor.
     */
    record EditorRequested() implements ReviewMsg {}

    /**
     * The editor ran {@code commands}, and saved the comment as {@code text}.
     */
    record EditorClosed(String text, List<ExecutedCommand> commands) implements ReviewMsg {}

    record EditorFailed(Failure failure) implements ReviewMsg {}

    /**
     * The comment was sent by running {@code commands}: a reply, or a thread started, whose id is {@code started}.
     */
    record Sent(ReviewCmd.Send cmd, Option<String> started, List<ExecutedCommand> commands) implements ReviewMsg {}

    /**
     * Sending the comment failed.
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
}
