package net.vanfleteren.jazygit.features.github.comments;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.features.github.browse.OpenLinkCmd;
import net.vanfleteren.jazygit.features.github.comment.CommentPopup;
import net.vanfleteren.jazygit.features.github.comments.CommentsCmd.LoadComments;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

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
                                PopupUpdate.ask(model, CommentsPopup.loading(shown))
                                        .model(),
                                new LoadComments(shown.pullRequest().number())))
                        .getOrElse(() -> Next.of(model));
            case CommentsMsg.Loaded(PrNumber number, var comments) ->
                Next.of(mapComments(model, number, popup -> popup.read(Either.right(comments))));
            case CommentsMsg.LoadFailed(PrNumber number, var failure) ->
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
                        .map(popup -> OperationUpdate.start(
                                model.clearError(),
                                new OpenPullRequest(popup.shown().branch(), popup.number())))
                        .getOrElse(() -> Next.of(model));
            // As opening the pull request: the popup stays open.
            case CommentsMsg.LinkChosen(String url) ->
                model.popup(CommentsPopup.class)
                        .map(popup -> OperationUpdate.start(model.clearError(), new OpenLinkCmd(url)))
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
    private static Model mapComments(Model model, PrNumber number, UnaryOperator<CommentsPopup> change) {
        return model.popup(CommentsPopup.class)
                .filter(popup -> popup.number().equals(number))
                .map(popup -> model.openPopup(change.apply(popup)))
                .orElse(() -> model.popup(CommentPopup.class)
                        .filter(popup -> popup.from() instanceof CommentsPopup from
                                && from.number().equals(number))
                        .map(popup -> model.openPopup(popup.withFrom(change.apply((CommentsPopup) popup.from())))))
                .getOrElse(model);
    }
}
