package net.vanfleteren.jazygit.feature.commit.create;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.stage.StageCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Committing what is staged, staging the files first when the user agreed to commit them all.
 */
public sealed interface CreateCommitCmd extends GitOperationCmd {

    Operation COMMIT = Operation.of("log.title.commit", "error.commit.failed");

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<String> paths) implements CreateCommitCmd {

        @Override
        public Operation operation() {
            return StageCmd.STAGE;
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

    record Commit(CommitMessage message) implements CreateCommitCmd {

        @Override
        public Operation operation() {
            return COMMIT;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.commit(message);
        }
    }
}
