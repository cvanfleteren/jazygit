package net.vanfleteren.jazygit.feature.github.comment;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd.AddComment;
import net.vanfleteren.jazygit.feature.github.comments.CommentsCmd.LoadComments;
import net.vanfleteren.jazygit.feature.github.comments.CommentsPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestUpdate;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposeUpdate;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.operation.*;

/**
 * Commenting on the pull request that is shown. The comment is written in a popup of its own, or in the editor, and
 * sent from that popup, which stays open until it is sent, so a comment that fails to send is not lost (ADR 115). Once
 * it is sent, the popup it was opened from comes back, the pull request dialog or the comments, and the details of the
 * pull request are read again to count the new comment; the comments are read again too, to show it.
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
            // A read of the pull request that started before the comment was sent is not taken; the one started now is
            // the dialog's own, read quietly (ADR 34).
            case CommentMsg.Sent(AddComment cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands)
                        .map(succeeded -> succeeded.mapGitHub(gitHub -> gitHub.withChanged(cmd.number())));
                yield done.model()
                        .popup(CommentPopup.class)
                        .filter(popup -> popup.number().equals(cmd.number()))
                        .map(popup -> done.then(changed -> PullRequestUpdate.read(changed, cmd.number())
                                .apply((given, load) -> new Next(
                                        given.openPopup(sentFrom(popup.from(), load.read(), model.now())),
                                        List.<Cmd>of(load)
                                                .appendAll(Option.when(
                                                        popup.from() instanceof CommentsPopup,
                                                        () -> new LoadComments(cmd.number())))))))
                        .getOrElse(done);
            }
            // The popup stays open, with the comment, to send it again or to copy it.
            case CommentMsg.SendFailed(AddComment cmd, Failure failure) ->
                OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                        .map(failed -> ComposeUpdate.unsent(failed, CommentPopup.class));
        };
    }

    /**
     * Sending the comment {@code popup} holds. A blank comment is not sent, as GitHub refuses it.
     */
    static Next send(Model model, CommentPopup popup) {
        return popup.text().isBlank()
                ? Next.of(model)
                : Update.start(
                        model.clearError().openPopup(popup.withSending(true)),
                        new AddComment(popup.pullRequestPopup().branch(), popup.number(), popup.text()));
    }

    /**
     * The popup {@code from} to go back to once a comment from it was sent: the comments go to the newest, the new
     * comment once they are read again; the pull request reads quietly from {@code now}, by read {@code read}.
     */
    private static ShowsPullRequest sentFrom(ShowsPullRequest from, long read, Instant now) {
        ShowsPullRequest back = from instanceof CommentsPopup comments ? comments.withHighlighted(Option.none()) : from;
        return back.withPullRequestPopup(back.pullRequestPopup().readingAgain(Reading.BY_ITSELF, now, read));
    }
}
