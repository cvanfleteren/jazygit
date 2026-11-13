package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Deleting a comment of a review thread (ADR 64). It keeps no branch busy: it does not touch one.
 */
public sealed interface DeleteCommentCmd extends Cmd {

    Operation DELETE_COMMENT = Operation.of("log.title.deleteReviewComment", "error.deleteReviewComment.failed");

    /**
     * Deletes the comment with the id {@code comment} of the thread with the id {@code thread} of pull request
     * {@code number} (ADR 64).
     */
    record DeleteComment(int number, ReviewThread.Id thread, ReviewThread.Comment.Id comment)
            implements DeleteCommentCmd, OperationCmd {

        @Override
        public Operation operation() {
            return DELETE_COMMENT;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number);
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .reviews()
                    .deleteRemark(comment)
                    .mapLeft(Failure::of)
                    .fold(this::failed, this::succeeded);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new DeleteCommentMsg.CommentDeleted(this, commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new DeleteCommentMsg.DeleteFailed(this, failure);
        }
    }
}
