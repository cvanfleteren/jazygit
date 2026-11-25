package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commands that resolve a conflict, or bring one back.
 */
public sealed interface ResolveCmd extends GitOperationCmd {

    Operation KEEP = Operation.of("midway.log.title.keep", "midway.error.keep.failed");
    Operation RESTORE = Operation.of("midway.log.title.restore", "midway.error.restore.failed");

    /**
     * Keeps {@code version}'s side of the file at {@code path}, in {@code conflict}.
     */
    record Keep(String path, Conflict conflict, Conflict.Version version) implements ResolveCmd {
        @Override
        public Operation operation() {
            return KEEP;
        }

        @Override
        public Option<String> subject() {
            return Option.of(path);
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().keep(path, conflict, version);
        }
    }

    /**
     * Brings back the conflict of the file at {@code path}, resolved since the operation stopped.
     */
    record Restore(String path) implements ResolveCmd {
        @Override
        public Operation operation() {
            return RESTORE;
        }

        @Override
        public Option<String> subject() {
            return Option.of(path);
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().restoreConflict(path);
        }
    }
}
