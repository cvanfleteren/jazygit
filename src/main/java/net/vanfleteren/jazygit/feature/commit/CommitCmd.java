package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.process.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.GitOperationCmd;

/**
 * Commands that create or rewrite commits.
 */
public sealed interface CommitCmd extends GitOperationCmd {

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<String> paths) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.STAGE;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.stage(paths);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new CommitMsg.StagedForCommit(this, commands);
        }
    }

    record Commit(CommitMessage message) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.COMMIT;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.commit(message);
        }
    }

    /**
     * Stages the paths, if any, then amends the last commit with the index, keeping its message.
     */
    record Amend(List<String> stage) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.AMEND;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return GitFailure.<ProcessError>sequence(() -> git.stage(stage), git::amendLastCommit);
        }
    }

    /**
     * Folds a commit into its parent: a squash, or a fixup when {@code mode} drops either message.
     */
    record Squash(Sha sha, SquashMode mode) implements CommitCmd {

        @Override
        public Operation operation() {
            return mode == SquashMode.SQUASH ? Operation.SQUASH : Operation.FIXUP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.squash(sha, mode);
        }
    }

    /**
     * Replaces the message of a commit.
     */
    record Reword(Sha sha, CommitMessage message) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.REWORD;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.reword(sha, message);
        }
    }
}
