package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.process.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.GitOperationCmd;

/**
 * Commands that change what is staged.
 */
public sealed interface StageCmd extends GitOperationCmd {

    record Stage(List<String> paths) implements StageCmd {

        @Override
        public Operation operation() {
            return Operation.STAGE;
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
            return Operation.UNSTAGE;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return GitFailure.<ProcessError>sequence(() -> git.unstageNew(added), () -> git.unstage(others));
        }
    }
}
