package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

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
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.stage(paths);
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
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return GitFailure.sequence(() -> git.unstageNew(added), () -> git.unstage(others));
        }
    }
}
