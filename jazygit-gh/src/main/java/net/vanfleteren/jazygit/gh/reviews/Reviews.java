package net.vanfleteren.jazygit.gh.reviews;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The conversation on a pull request and its review threads: reading them, commenting, reviewing, and changing what
 * was said (ADR 114).
 */
public interface Reviews {

    /**
     * The conversation on pull request {@code number}: its description, unless it is empty, then its comments and
     * the reviews that say something, oldest first. A review says something when it has text, approves, asks for
     * changes or was dismissed; pending reviews, which only their author sees, are left out.
     */
    Either<GhError, List<PullRequestComment>> conversation(int number);

    /**
     * Adds a comment saying {@code body} to the conversation of pull request {@code number}.
     */
    Either<GhFailure, List<ExecutedCommand>> comment(int number, String body);

    /**
     * Deletes the comment of the conversation on a pull request with node id {@code comment}; one that is gone already
     * counts as deleted (ADR 64).
     */
    Either<GhFailure, List<ExecutedCommand>> deleteComment(PullRequestComment.Id comment);

    /**
     * Changes the comment of the conversation on a pull request with node id {@code comment} to {@code body}
     * (ADR 65).
     */
    Either<GhFailure, Edited> editComment(PullRequestComment.Id comment, String body);

    /**
     * Submits a review saying {@code verdict}, with {@code body}, which may be empty to approve, on the pull request
     * with node id {@code pullRequest} (ADR 100).
     */
    Either<GhFailure, List<ExecutedCommand>> submit(String pullRequest, Verdict verdict, String body);

    /**
     * The review threads of pull request {@code number}, on lines of its diff or on whole files, with the commits its
     * changes are diffed between (ADR 55). The comments of a review that is not submitted yet, which only their author
     * sees, are left out, and so is a thread of only those.
     */
    Either<GhError, ReviewRemarks> remarks(int number);

    /**
     * Starts {@code thread} on pull request {@code number}, at its head commit {@code commit}, published at once
     * (ADR 60).
     */
    Either<GhFailure, List<ExecutedCommand>> startThread(int number, GhCommit commit, NewReviewThread thread);

    /**
     * Replies {@code body} to the review thread of pull request {@code number} whose first comment has the id
     * {@code comment}, as REST numbers it, published at once (ADR 60).
     */
    Either<GhFailure, List<ExecutedCommand>> reply(int number, ReviewThread.Comment.RestId comment, String body);

    /**
     * Resolves the review thread with node id {@code thread} when {@code resolved}, else unresolves it (ADR 60).
     */
    Either<GhFailure, List<ExecutedCommand>> resolve(ReviewThread.Id thread, boolean resolved);

    /**
     * Deletes the comment of a review thread with node id {@code comment}; one that is gone already counts as deleted
     * (ADR 64).
     */
    Either<GhFailure, List<ExecutedCommand>> deleteRemark(ReviewThread.Comment.Id comment);

    /**
     * Gives {@code reaction} to the comment of a review thread with node id {@code comment} when {@code given}, else
     * takes it back (ADR 86); a comment GitHub no longer has fails as {@link GhError.Gone}.
     */
    Either<GhFailure, List<ExecutedCommand>> react(ReviewThread.Comment.Id comment, Reaction reaction, boolean given);

    /**
     * Changes the comment of a review thread with node id {@code comment} to {@code body} (ADR 65).
     */
    Either<GhFailure, Edited> editRemark(ReviewThread.Comment.Id comment, String body);

    /**
     * A comment that was edited.
     *
     * @param body     its body, as GitHub now holds it
     * @param commands the command log
     */
    record Edited(String body, List<ExecutedCommand> commands) {}
}
