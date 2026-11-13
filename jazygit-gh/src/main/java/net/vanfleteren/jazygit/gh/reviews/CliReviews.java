package net.vanfleteren.jazygit.gh.reviews;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.process.GhCommand;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.gh.process.GhProcess;
import net.vanfleteren.jazygit.json.Decoder;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Reviews} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
public final class CliReviews implements Reviews {

    // Deletes a comment of the conversation by the node id it is read with (ADR 64).
    static final String DELETE_COMMENT = """
            mutation($comment: ID!) { deleteIssueComment(input: { id: $comment }) { clientMutationId } }""";

    // Edits a comment of the conversation by the node id it is read with (ADR 65).
    static final String EDIT_COMMENT = """
            mutation($comment: ID!, $body: String!) {
              updateIssueComment(input: { id: $comment, body: $body }) { issueComment { body } }
            }""";

    // The body of the comment of the conversation, as GitHub holds it once it is edited.
    static final Decoder<String> EDITED_COMMENT =
            Decoder.at("data.updateIssueComment.issueComment.body", Decoder.string());

    private final GhProcess process;

    public CliReviews(GhProcess process) {
        this.process = process;
    }

    static GhCommand commentCommand(int number, String body) {
        return gh("pr", "comment", String.valueOf(number), "--body", body);
    }

    static GhCommand deleteCommentCommand(PullRequestComment.Id comment) {
        return gh("api", "graphql", "-f", "query=" + DELETE_COMMENT, "-f", "comment=" + comment.value());
    }

    static GhCommand editCommentCommand(PullRequestComment.Id comment, String body) {
        return gh(
                "api",
                "graphql",
                "-f",
                "query=" + EDIT_COMMENT,
                "-f",
                "comment=" + comment.value(),
                "-f",
                "body=" + body);
    }

    @Override
    public Either<GhError, List<PullRequestComment>> conversation(int number) {
        return process.readJson(CommentsQuery.command(number), CommentsQuery.DECODER);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> comment(int number, String body) {
        return process.log(commentCommand(number, body));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> deleteComment(PullRequestComment.Id comment) {
        return delete(deleteCommentCommand(comment));
    }

    @Override
    public Either<GhFailure, Edited> editComment(PullRequestComment.Id comment, String body) {
        return edit(editCommentCommand(comment, body), EDITED_COMMENT);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> submit(String pullRequest, Verdict verdict, String body) {
        return process.log(ReviewMutation.submit(pullRequest, verdict, body));
    }

    @Override
    public Either<GhError, ReviewRemarks> remarks(int number) {
        return process.readJson(ReviewThreadsQuery.command(number), ReviewThreadsQuery.DECODER);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> startThread(int number, GhCommit commit, NewReviewThread thread) {
        return process.log(ReviewThreadMutations.start(number, commit, thread));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> reply(
            int number, ReviewThread.Comment.RestId comment, String body) {
        return process.log(ReviewThreadMutations.reply(number, comment, body));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> resolve(ReviewThread.Id thread, boolean resolved) {
        return process.log(ReviewThreadMutations.resolve(thread, resolved));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> deleteRemark(ReviewThread.Comment.Id comment) {
        return delete(ReviewThreadMutations.delete(comment));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> react(
            ReviewThread.Comment.Id comment, Reaction reaction, boolean given) {
        return process.log(ReviewThreadMutations.react(comment, reaction, given))
                .mapLeft(failure -> GhProcess.gone(failure.error())
                        ? new GhFailure(new GhError.Gone(), failure.commands())
                        : failure);
    }

    @Override
    public Either<GhFailure, Edited> editRemark(ReviewThread.Comment.Id comment, String body) {
        return edit(ReviewThreadMutations.edit(comment, body), ReviewThreadMutations.EDITED);
    }

    // Runs command, which edits a comment, and gives the body GitHub now holds, which decoder reads from what it says.
    // A comment GitHub no longer has is gone: unlike a delete, its edit is not done.
    private Either<GhFailure, Edited> edit(GhCommand command, Decoder<String> decoder) {
        return process.run(command)
                .mapLeft(failure -> GhProcess.gone(failure.error())
                        ? new GhFailure(new GhError.Gone(), failure.commands())
                        : failure)
                .flatMap(output -> GhProcess.decoded(output.stdout(), decoder)
                        .map(body -> new Edited(body, output.log()))
                        .mapLeft(unexpected -> new GhFailure(unexpected, output.log())));
    }

    // Runs command, which deletes a comment: one GitHub no longer has was deleted already, as by someone else.
    private Either<GhFailure, List<ExecutedCommand>> delete(GhCommand command) {
        return process.log(command)
                .fold(
                        failure -> GhProcess.gone(failure.error())
                                ? Either.right(failure.commands())
                                : Either.left(failure),
                        Either::right);
    }
}
