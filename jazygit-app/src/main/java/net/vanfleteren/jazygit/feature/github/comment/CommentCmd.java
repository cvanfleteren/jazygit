package net.vanfleteren.jazygit.feature.github.comment;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Writing a comment on a pull request in the editor, and adding it.
 */
public sealed interface CommentCmd extends Cmd {

    // The branch is not busy meanwhile: a comment does not touch it, so it waits for nothing done to it, nor holds
    // anything up; the popup tells it is being sent.
    Operation COMMENT_PR = Operation.of("log.title.commentPr", "error.commentPr.failed");

    Operation EDIT_COMMENT = Operation.of("log.title.editComment", "error.editComment.failed");

    /**
     * The file the comment is written in, in the editor; editors take it for Markdown, as GitHub does.
     */
    String FILE_NAME = "PR_COMMENT.md";

    /**
     * Opens {@code text} in git's editor, with the terminal handed over to it, and reports how it was saved.
     */
    record EditComment(String text) implements CommentCmd {

        @Override
        public Msg run(Env env) {
            return env.terminal()
                    .handOver(() -> env.git().editText(FILE_NAME, text))
                    .fold(this::failedWith, edited -> new CommentMsg.EditorClosed(edited._1, edited._2));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommentMsg.EditorFailed(failure);
        }
    }

    /**
     * Adds a comment saying {@code body} to pull request {@code number}, from {@code branch}.
     */
    record AddComment(BranchName branch, int number, String body) implements CommentCmd, OperationCmd {

        @Override
        public Operation operation() {
            return COMMENT_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch.value());
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().commentOnPullRequest(number, body).fold(this::failedWith, this::succeeded);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new CommentMsg.Sent(this, commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommentMsg.SendFailed(this, failure);
        }
    }
}
