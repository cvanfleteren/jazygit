package net.vanfleteren.jazygit.features.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.Reaction;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Giving a reaction to a comment of a review thread, or taking it back (ADR 86). It keeps no branch busy: it does not
 * touch one.
 */
public sealed interface ReactCmd extends Cmd {

    Operation REACT = Operation.of("github.log.title.reactReviewComment", "github.error.reactReviewComment.failed");

    Operation UNREACT =
            Operation.of("github.log.title.unreactReviewComment", "github.error.unreactReviewComment.failed");

    /**
     * Gives {@code reaction} to the comment with the id {@code comment} of the thread with the id {@code thread} of
     * pull request {@code number} when {@code given}, else takes it back (ADR 86).
     */
    record React(
            PrNumber number, ReviewThread.Id thread, ReviewThread.Comment.Id comment, Reaction reaction, boolean given)
            implements ReactCmd, OperationCmd {

        @Override
        public Operation operation() {
            return given ? REACT : UNREACT;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number.value());
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .reviews()
                    .react(comment, reaction, given)
                    .mapLeft(Failure::of)
                    .fold(this::failed, this::succeeded);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new ReactMsg.Reacted(this, commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new ReactMsg.ReactFailed(this, failure);
        }
    }
}
