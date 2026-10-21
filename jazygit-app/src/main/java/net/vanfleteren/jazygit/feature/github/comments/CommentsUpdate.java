package net.vanfleteren.jazygit.feature.github.comments;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.github.comment.CommentPopup;
import net.vanfleteren.jazygit.feature.github.comments.CommentsCmd.LoadComments;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reading the conversation on the pull request that is shown, in a popup of its own, opened from the pull request
 * dialog and going back to it. The comments are read each time it opens, and not kept.
 */
public final class CommentsUpdate {

    private CommentsUpdate() {}

    public static Next update(Model model, CommentsMsg msg) {
        return switch (msg) {
            // Once the details are read, as commenting: whatever state the pull request is in.
            case CommentsMsg.Requested() ->
                model.popup(PullRequestPopup.class)
                        .filter(PullRequestPopup::detailsRead)
                        .map(shown -> Next.of(
                                Update.ask(model, CommentsPopup.loading(shown)).model(),
                                new LoadComments(shown.pullRequest().number())))
                        .getOrElse(() -> Next.of(model));
            case CommentsMsg.Loaded(int number, var comments) ->
                Next.of(mapComments(model, number, popup -> popup.read(Either.right(comments))));
            case CommentsMsg.LoadFailed(int number, var failure) ->
                Next.of(mapComments(model, number, popup -> popup.read(Either.left(failure))));
            case CommentsMsg.Highlighted(var id) ->
                Next.of(model.popup(CommentsPopup.class)
                        .filter(popup ->
                                popup.all().exists(comment -> comment.id().equals(id)))
                        .map(popup -> model.openPopup(popup.withHighlighted(Option.of(id))))
                        .getOrElse(model));
            // The popup stays open, to go on reading.
            case CommentsMsg.OpenChosen() ->
                model.popup(CommentsPopup.class)
                        .map(popup -> Update.start(
                                model.clearError(),
                                new OpenPullRequest(popup.shown().branch(), popup.number())))
                        .getOrElse(() -> Next.of(model));
            case CommentsMsg.Closed() ->
                Next.of(model.popup(CommentsPopup.class)
                        .map(popup -> model.openPopup(popup.shown()))
                        .getOrElse(model));
        };
    }

    /**
     * {@code model} with the comments on pull request {@code number} as {@code change} makes them: the popup that is
     * open, or the one a comment goes back to. Unchanged when neither shows them, as the user went back meanwhile.
     */
    private static Model mapComments(Model model, int number, UnaryOperator<CommentsPopup> change) {
        return model.popup(CommentsPopup.class)
                .filter(popup -> popup.number() == number)
                .map(popup -> model.openPopup(change.apply(popup)))
                .orElse(() -> model.popup(CommentPopup.class)
                        .filter(popup -> popup.from() instanceof CommentsPopup from && from.number() == number)
                        .map(popup -> model.openPopup(popup.withFrom(change.apply((CommentsPopup) popup.from())))))
                .getOrElse(model);
    }
}
