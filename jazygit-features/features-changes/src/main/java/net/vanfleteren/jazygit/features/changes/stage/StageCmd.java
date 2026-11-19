package net.vanfleteren.jazygit.features.changes.stage;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.files.Index;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commands that change what is staged.
 */
public sealed interface StageCmd extends GitOperationCmd {

    Operation STAGE = Operation.of("changes.log.title.stage", "changes.error.staging.failed");

    Operation UNSTAGE = Operation.of("changes.log.title.unstage", "changes.error.staging.failed");

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
