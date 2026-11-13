package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.files.Index;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Commands that change what is staged.
 */
public sealed interface StageCmd extends GitOperationCmd {

    Operation STAGE = Operation.of("log.title.stage", "error.staging.failed");

    Operation UNSTAGE = Operation.of("log.title.unstage", "error.staging.failed");

    record Stage(List<String> paths) implements StageCmd {

        @Override
        public Operation operation() {
            return STAGE;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().stage(paths);
        }
    }

    /**
     * Takes paths out of the index: the {@code added} ones (not in HEAD) become untracked again, the
     * {@code others} are reset to HEAD.
     */
    record Unstage(List<String> added, List<String> others) implements StageCmd {

        @Override
        public Operation operation() {
            return UNSTAGE;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            Index index = git.index();
            return GitFailure.sequence(() -> index.unstageNew(added), () -> index.unstage(others));
        }
    }
}
