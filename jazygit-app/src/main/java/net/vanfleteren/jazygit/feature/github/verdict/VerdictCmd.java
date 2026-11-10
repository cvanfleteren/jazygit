package net.vanfleteren.jazygit.feature.github.verdict;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Writing the text of a review in the editor, and submitting the review (ADR 100).
 */
public sealed interface VerdictCmd extends Cmd {

    // The branch is not busy meanwhile, as for a comment: a review does not touch it; the popup tells it is sent.
    Operation REVIEW_PR = Operation.of("log.title.reviewPr", "error.reviewPr.failed");

    /**
     * The file the text is written in, in the editor; editors take it for Markdown, as GitHub does.
     */
    String FILE_NAME = "PR_REVIEW.md";

    /**
     * Opens {@code text} in git's editor, with the terminal handed over to it, and reports how it was saved.
     */
    record EditReview(String text) implements VerdictCmd {

        @Override
        public Msg run(Env env) {
            return env.terminal()
                    .handOver(() -> env.git().editor().editText(FILE_NAME, text))
                    .mapLeft(Failure::of)
                    .fold(this::failed, edited -> new VerdictMsg.EditorClosed(edited._1, edited._2));
        }

        @Override
        public Msg failed(Failure failure) {
            return new VerdictMsg.EditorFailed(failure);
        }
    }

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
                    .submitReview(pullRequest, verdict, body)
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
