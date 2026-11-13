package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.chosen;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.gone;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.mapPullRequest;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.refused;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.review.DeleteCommentCmd.DeleteComment;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.operation.*;

/**
 * Deleting a comment of a review thread of the diff of a pull request's files, from its cursor (ADR 64). It is asked
 * about first, and asked again as the comment is once confirmed. It is deleted at any commits, and taken out of the
 * threads once GitHub took it, without reading them again.
 */
public final class DeleteCommentUpdate {

    private DeleteCommentUpdate() {}

    public static Next update(Model model, DeleteCommentMsg msg) {
        return switch (msg) {
            case DeleteCommentMsg.DeleteAsked(DiffCursorAt at) -> deleteAsked(model, at);
            case DeleteCommentMsg.DeleteConfirmed() -> deleteConfirmed(model);
            case DeleteCommentMsg.CommentDeleted(DeleteComment cmd, var commands) ->
                commentDeleted(model, cmd, commands);
            case DeleteCommentMsg.DeleteFailed(DeleteComment cmd, Failure failure) -> deleteFailed(model, cmd, failure);
        };
    }

    /**
     * Deleting the comment of the cursor {@code at} asked for: asked about first.
     */
    private static Next deleteAsked(Model model, DiffCursorAt at) {
        return deletable(model, at.cursor())
                .fold(
                        why -> Next.of(refused(model, why)),
                        on -> Update.ask(
                                model,
                                new DeleteCommentPopup(on.number(), on.thread().id(), on.comment(), model.now())));
    }

    /**
     * Deleting the comment the popup asks about, confirmed, if it is open.
     */
    private static Next deleteConfirmed(Model model) {
        return model.popup(DeleteCommentPopup.class)
                .map(popup -> confirmDelete(model, popup))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code cmd} deleted its comment. Not read again: what GitHub did is known (ADR 64). The cursor on it goes to its
     * thread, or its line. Unless it was the last comment read of a thread GitHub has more of: which of those is its
     * first now, that a reply goes to, is not known until the threads are read again.
     */
    private static Next commentDeleted(Model model, DeleteComment cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> mapPullRequest(
                        done,
                        cmd.number(),
                        view -> view.withDeleting(view.deleting().remove(cmd.comment()))))
                .then(done -> gone(done, cmd.number(), cmd.thread(), cmd.comment()));
    }

    /**
     * {@code cmd} failed to delete its comment, which is no longer being deleted.
     */
    private static Next deleteFailed(Model model, DeleteComment cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(failed -> mapPullRequest(
                        failed,
                        cmd.number(),
                        view -> view.withDeleting(view.deleting().remove(cmd.comment()))));
    }

    /**
     * The comment the cursor {@code on} is on, of the files listed, with its thread and those files, if the viewer may
     * delete it now (ADR 64).
     */
    private static Either<Why, OnComment> deletable(Model model, Anchor on) {
        return chosen(model, on).filterOrElse(found -> found.comment().rights().delete(), found -> Why.NO_DELETE);
    }

    /**
     * Deleting the comment {@code popup} asks about, confirmed: asked again as it is now, as the comment may have gone,
     * or something be sent for its thread, meanwhile. It says it is being deleted until GitHub answers.
     */
    private static Next confirmDelete(Model model, DeleteCommentPopup popup) {
        Anchor on = new Anchor.Comment(popup.comment().id(), new Anchor.Thread(popup.thread(), Option.none()));
        return deletable(model, on)
                .filterOrElse(found -> found.number() == popup.number(), found -> Why.NOT_READ)
                .fold(
                        why -> Next.of(refused(model.withoutPopup(DeleteCommentPopup.class), why)),
                        found -> Update.confirm(
                                        model,
                                        DeleteCommentPopup.class,
                                        open -> Option.of(new DeleteComment(
                                                open.number(),
                                                open.thread(),
                                                open.comment().id())))
                                .map(started -> mapPullRequest(
                                        started,
                                        popup.number(),
                                        view -> view.withDeleting(view.deleting()
                                                .add(found.comment().id())))));
    }
}
