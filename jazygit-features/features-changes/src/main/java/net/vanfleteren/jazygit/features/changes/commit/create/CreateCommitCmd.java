package net.vanfleteren.jazygit.features.changes.commit.create;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.CommitMessage;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.features.changes.stage.StageCmd;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Committing what is staged, staging the files first when the user agreed to commit them all.
 */
public sealed interface CreateCommitCmd extends GitOperationCmd {

    Operation COMMIT = Operation.of("changes.log.title.commit", "changes.error.commit.failed");

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<RepoPath> paths) implements CreateCommitCmd {

        @Override
        public Operation operation() {
            return StageCmd.STAGE;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().stage(paths);
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
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.commits().commit(message);
        }
    }
}
