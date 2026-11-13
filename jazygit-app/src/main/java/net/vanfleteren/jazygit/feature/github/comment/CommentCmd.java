package net.vanfleteren.jazygit.feature.github.comment;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.compose.EditorFile;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Adding a comment to a pull request.
 */
public sealed interface CommentCmd extends Cmd {

    // The branch is not busy meanwhile: a comment does not touch it, so it waits for nothing done to it, nor holds
    // anything up; the popup tells it is being sent.
    Operation COMMENT_PR = Operation.of("log.title.commentPr", "error.commentPr.failed");

    Operation EDIT_COMMENT = Operation.of("log.title.editComment", "error.editComment.failed");

    /**
     * The file the comment is written in, in the editor (ADR 115).
     */
    EditorFile EDITOR = new EditorFile("PR_COMMENT.md", EDIT_COMMENT);

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
            return env.gitHub()
                    .reviews()
                    .comment(number, body)
                    .mapLeft(Failure::of)
                    .fold(this::failed, this::succeeded);
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
