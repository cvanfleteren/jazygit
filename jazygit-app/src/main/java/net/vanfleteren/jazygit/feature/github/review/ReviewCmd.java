package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Writing a comment on the lines of a pull request in the editor, sending it, and resolving a thread (ADR 60). None
 * keeps a branch busy: they do not touch one.
 */
public sealed interface ReviewCmd extends Cmd {

    Operation START = Operation.of("log.title.startThread", "error.startThread.failed");

    Operation REPLY = Operation.of("log.title.replyThread", "error.replyThread.failed");

    Operation RESOLVE = Operation.of("log.title.resolveThread", "error.resolveThread.failed");

    Operation UNRESOLVE = Operation.of("log.title.unresolveThread", "error.unresolveThread.failed");

    Operation EDIT_COMMENT = Operation.of("log.title.editReviewComment", "error.editReviewComment.failed");

    /**
     * The file the comment is written in, in the editor; editors take it for Markdown, as GitHub does.
     */
    String FILE_NAME = "PR_REVIEW_COMMENT.md";

    /**
     * Opens {@code text} in git's editor, with the terminal handed over to it, and reports how it was saved.
     */
    record EditComment(String text) implements ReviewCmd {

        @Override
        public Msg run(Env env) {
            return env.terminal()
                    .handOver(() -> env.git().editText(FILE_NAME, text))
                    .fold(this::failedWith, edited -> new ReviewMsg.EditorClosed(edited._1, edited._2));
        }

        @Override
        public Msg failed(Failure failure) {
            return new ReviewMsg.EditorFailed(failure);
        }
    }

    /**
     * Sends {@code body} to {@code target} of pull request {@code number}: a reply, or a thread started.
     */
    record Send(int number, Target target, String body) implements ReviewCmd, OperationCmd {

        @Override
        public Operation operation() {
            return target instanceof Target.Reply ? REPLY : START;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number);
        }

        @Override
        public Msg run(Env env) {
            return switch (target) {
                case Target.Reply(String thread) ->
                    env.gitHub().replyToReviewThread(thread, body).fold(this::failedWith, this::succeeded);
                case Target.Start(String pullRequest, String path, var lines) ->
                    env.gitHub()
                            .startReviewThread(pullRequest, new NewReviewThread(path, lines, body))
                            .fold(
                                    this::failedWith,
                                    started ->
                                            new ReviewMsg.Sent(this, Option.of(started.thread()), started.commands()));
            };
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new ReviewMsg.Sent(this, Option.none(), commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new ReviewMsg.SendFailed(this, failure);
        }
    }

    /**
     * Resolves the thread with the id {@code thread} of pull request {@code number} when {@code resolved}, else
     * unresolves it.
     */
    record Resolve(int number, String thread, boolean resolved) implements ReviewCmd, OperationCmd {

        @Override
        public Operation operation() {
            return resolved ? RESOLVE : UNRESOLVE;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number);
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().resolveReviewThread(thread, resolved).fold(this::failedWith, this::succeeded);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new ReviewMsg.Resolved(this, commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new ReviewMsg.ResolveFailed(this, failure);
        }
    }
}
