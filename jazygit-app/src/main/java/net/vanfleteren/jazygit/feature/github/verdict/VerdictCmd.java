package net.vanfleteren.jazygit.feature.github.verdict;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.comment.CommentCmd;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.compose.EditorFile;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Submitting a review (ADR 100).
 */
public sealed interface VerdictCmd extends Cmd {

    // The branch is not busy meanwhile, as for a comment: a review does not touch it; the popup tells it is sent.
    Operation REVIEW_PR = Operation.of("log.title.reviewPr", "error.reviewPr.failed");

    /**
     * The file the text is written in, in the editor (ADR 115). It is logged as writing a comment is.
     */
    EditorFile EDITOR = new EditorFile("PR_REVIEW.md", CommentCmd.EDIT_COMMENT);

    /**
     * Submits a review saying {@code verdict}, with {@code body}, on pull request {@code number}, whose node id is
     * {@code pullRequest}.
     */
    record SubmitReview(int number, String pullRequest, Verdict verdict, String body)
            implements VerdictCmd, OperationCmd {

        @Override
        public Operation operation() {
            return REVIEW_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number);
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .reviews()
                    .submit(pullRequest, verdict, body)
                    .mapLeft(Failure::of)
                    .fold(this::failed, this::succeeded);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new VerdictMsg.Sent(this, commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new VerdictMsg.SendFailed(this, failure);
        }
    }
}
