package net.vanfleteren.jazygit.gh.reviews;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Reviews} for tests: pull requests without a conversation or review threads, whose operations all succeed
 * without running anything. Fakes override what they need.
 */
public interface StubReviews extends Reviews {

    /**
     * No description, comments or reviews.
     */
    @Override
    default Either<GhError, List<PullRequestComment>> conversation(PrNumber number) {
        return Either.right(List.empty());
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> comment(PrNumber number, String body) {
        return Either.right(List.of(CliReviews.commentCommand(number, body).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> deleteComment(PullRequestComment.Id comment) {
        return Either.right(List.of(CliReviews.deleteCommentCommand(comment).executed()));
    }

    @Override
    default Either<GhFailure, Edited> editComment(PullRequestComment.Id comment, String body) {
        return Either.right(new Edited(
                body, List.of(CliReviews.editCommentCommand(comment, body).executed())));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> submit(String pullRequest, Verdict verdict, String body) {
        return Either.right(
                List.of(ReviewMutation.submit(pullRequest, verdict, body).executed()));
    }

    /**
     * No review threads, at commits that are not known.
     */
    @Override
    default Either<GhError, ReviewRemarks> remarks(PrNumber number) {
        return Either.right(ReviewRemarks.none());
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> startThread(
            PrNumber number, GhCommit commit, NewReviewThread thread) {
        return Either.right(
                List.of(ReviewThreadMutations.start(number, commit, thread).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> reply(
            PrNumber number, ReviewThread.Comment.RestId comment, String body) {
        return Either.right(
                List.of(ReviewThreadMutations.reply(number, comment, body).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> resolve(ReviewThread.Id thread, boolean resolved) {
        return Either.right(
                List.of(ReviewThreadMutations.resolve(thread, resolved).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> deleteRemark(ReviewThread.Comment.Id comment) {
        return Either.right(List.of(ReviewThreadMutations.delete(comment).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> react(
            ReviewThread.Comment.Id comment, Reaction reaction, boolean given) {
        return Either.right(
                List.of(ReviewThreadMutations.react(comment, reaction, given).executed()));
    }

    @Override
    default Either<GhFailure, Edited> editRemark(ReviewThread.Comment.Id comment, String body) {
        return Either.right(new Edited(
                body, List.of(ReviewThreadMutations.edit(comment, body).executed())));
    }
}
