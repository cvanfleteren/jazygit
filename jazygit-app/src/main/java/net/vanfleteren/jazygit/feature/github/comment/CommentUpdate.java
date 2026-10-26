package net.vanfleteren.jazygit.feature.github.comment;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd.AddComment;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd.EditComment;
import net.vanfleteren.jazygit.feature.github.comments.CommentsCmd.LoadComments;
import net.vanfleteren.jazygit.feature.github.comments.CommentsPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestUpdate;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Commenting on the pull request that is shown. The comment is written in a popup of its own, or in the editor, and
 * sent from that popup, which stays open until it is sent, so a comment that fails to send is not lost. Once it is
 * sent, the popup it was opened from comes back, the pull request dialog or the comments, and the details of the pull
 * request are read again to count the new comment; the comments are read again too, to show it.
 */
public final class CommentUpdate {

    private CommentUpdate() {}

    public static Next update(Model model, CommentMsg msg) {
        return switch (msg) {
            // Once the details are read, whatever state the pull request is in: closed ones take comments too. The
            // comments are only shown once the details are read.
            case CommentMsg.Requested() ->
                Update.ask(
                        model,
                        model.popup(PullRequestPopup.class)
                                .filter(PullRequestPopup::detailsRead)
                                .<ShowsPullRequest>map(shown -> shown)
                                .orElse(() -> model.popup(CommentsPopup.class))
                                .map(CommentPopup::on));
            // While it is being sent, the comment stays as it is sent.
            case CommentMsg.Edited(String text) ->
                Next.of(editable(model)
                        .map(popup -> model.openPopup(popup.withText(text)))
                        .getOrElse(model));
            // A blank comment is not sent, as GitHub refuses it.
            case CommentMsg.Confirmed() ->
                editable(model)
                        .filter(popup -> !popup.text().isBlank())
                        .map(popup -> Update.start(
                                model.clearError().openPopup(popup.withSending(true)),
                                new AddComment(popup.pullRequestPopup().branch(), popup.number(), popup.text())))
                        .getOrElse(() -> Next.of(model));
            case CommentMsg.Cancelled() ->
                Next.of(editable(model)
                        .map(popup -> model.openPopup(popup.from()))
                        .getOrElse(model));
            case CommentMsg.EditorRequested() ->
                editable(model)
                        .map(popup -> Next.of(model.clearError(), new EditComment(popup.text())))
                        .getOrElse(() -> Next.of(model));
            // Cancelled meanwhile, the popup stays closed.
            case CommentMsg.EditorClosed(String text, var commands) -> {
                Model logged = CommandLog.append(model, CommentCmd.EDIT_COMMENT, commands);
                yield Next.of(changed(logged, popup -> popup.withText(text)));
            }
            case CommentMsg.EditorFailed(Failure failure) ->
                Next.of(CommandLog.append(model, CommentCmd.EDIT_COMMENT, failure.commands())
                        .fail(new AppError.OperationFailed(CommentCmd.EDIT_COMMENT, Option.none(), failure)));
            // A read of the pull request that started before the comment was sent is not taken (ADR 34).
            case CommentMsg.Sent(AddComment cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model changed = done.model().mapGitHub(gitHub -> gitHub.withChanged(cmd.number()));
                yield changed.popup(CommentPopup.class)
                        .filter(popup -> popup.number() == cmd.number())
                        .map(popup -> PullRequestUpdate.read(changed, cmd.number())
                                .apply((given, load) -> new Next(
                                        given.openPopup(sentFrom(popup.from())),
                                        done.cmds()
                                                .append(load)
                                                .appendAll(Option.when(
                                                        popup.from() instanceof CommentsPopup,
                                                        () -> new LoadComments(cmd.number()))))))
                        .getOrElse(() -> new Next(changed, done.cmds()));
            }
            // The popup stays open, with the comment, to send it again or to copy it.
            case CommentMsg.SendFailed(AddComment cmd, Failure failure) -> {
                Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure));
                yield new Next(changed(failed.model(), popup -> popup.withSending(false)), failed.cmds());
            }
        };
    }

    /**
     * The popup {@code from} to go back to once a comment from it was sent: the comments go to the newest, the new
     * comment once they are read again.
     */
    private static ShowsPullRequest sentFrom(ShowsPullRequest from) {
        return from instanceof CommentsPopup comments ? comments.withHighlighted(Option.none()) : from;
    }

    /**
     * The comment popup, if it is open and not being sent.
     */
    private static Option<CommentPopup> editable(Model model) {
        return model.popup(CommentPopup.class).filter(popup -> !popup.sending());
    }

    /**
     * {@code model} with the comment popup as {@code change} makes it, if it is open.
     */
    private static Model changed(Model model, Function<CommentPopup, CommentPopup> change) {
        return model.popup(CommentPopup.class)
                .map(popup -> model.openPopup(change.apply(popup)))
                .getOrElse(model);
    }
}
