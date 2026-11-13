package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.review.ReviewPopup.Target;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.compose.EditorFile;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Sending a comment on the lines of a pull request, resolving a thread (ADR 60), and saving the edit of a comment
 * (ADR 65). None keeps a branch busy: they do not touch one.
 */
public sealed interface ReviewCmd extends Cmd {

    Operation START = Operation.of("log.title.startThread", "error.startThread.failed");

    Operation REPLY = Operation.of("log.title.replyThread", "error.replyThread.failed");

    Operation RESOLVE = Operation.of("log.title.resolveThread", "error.resolveThread.failed");

    Operation UNRESOLVE = Operation.of("log.title.unresolveThread", "error.unresolveThread.failed");

    // Writing a comment in the editor, with Ctrl+O; not saving the edit of one, which UPDATE_COMMENT is.
    Operation EDIT_COMMENT = Operation.of("log.title.editReviewComment", "error.editReviewComment.failed");

    // Saving the edit of a comment with GitHub (ADR 65).
    Operation UPDATE_COMMENT = Operation.of("log.title.updateReviewComment", "error.updateReviewComment.failed");

    /**
     * The file the comment is written in, in the editor (ADR 115).
     */
    EditorFile EDITOR = new EditorFile("PR_REVIEW_COMMENT.md", EDIT_COMMENT);

    /**
     * Sends {@code body} to {@code target} of pull request {@code number}: a reply, a thread started, or the edit of a
     * comment, which GitHub answers with the body it holds.
     */
    record Send(PrNumber number, Target target, String body) implements ReviewCmd, OperationCmd {

        @Override
        public Operation operation() {
            return switch (target) {
                case Target.Reply reply -> REPLY;
                case Target.Start start -> START;
                case Target.Edit edit -> UPDATE_COMMENT;
            };
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number.value());
        }

        @Override
        public Msg run(Env env) {
            return switch (target) {
                case Target.Reply(var ignored, ReviewThread.Comment.RestId comment) ->
                    env.gitHub()
                            .reviews()
                            .reply(number, comment, body)
                            .mapLeft(Failure::of)
                            .fold(this::failed, this::succeeded);
                case Target.Start(Sha commit, String path, var lines) ->
                    env.gitHub()
                            .reviews()
                            .startThread(number, new GhCommit(commit.value()), new NewReviewThread(path, lines, body))
                            .mapLeft(Failure::of)
                            .fold(this::failed, this::succeeded);
                case Target.Edit(var ignored, ReviewThread.Comment.Id comment, String original) ->
                    env.gitHub()
                            .reviews()
                            .editRemark(comment, body)
                            .mapLeft(Failure::of)
                            .fold(
                                    this::failed,
                                    edited -> new ReviewMsg.CommentEdited(this, edited.body(), edited.commands()));
            };
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new ReviewMsg.Sent(this, commands);
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
    record Resolve(PrNumber number, ReviewThread.Id thread, boolean resolved) implements ReviewCmd, OperationCmd {

        @Override
        public Operation operation() {
            return resolved ? RESOLVE : UNRESOLVE;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + number.value());
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .reviews()
                    .resolve(thread, resolved)
                    .mapLeft(Failure::of)
                    .fold(this::failed, this::succeeded);
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
