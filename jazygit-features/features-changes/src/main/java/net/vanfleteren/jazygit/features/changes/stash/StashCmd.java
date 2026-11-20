package net.vanfleteren.jazygit.features.changes.stash;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commands that create, apply and delete stashes.
 */
public sealed interface StashCmd extends GitOperationCmd {

    Operation STASH = Operation.of("changes.log.title.stash", "changes.error.stash.failed");

    Operation STASH_POP = Operation.of("changes.log.title.stashPop", "changes.error.stashPop.failed");

    Operation STASH_DROP = Operation.of("changes.log.title.stashDrop", "changes.error.stashDrop.failed");

    /**
     * Stashes the changes under {@code name}.
     */
    record Stash(Option<String> name) implements StashCmd {

        @Override
        public Operation operation() {
            return STASH;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.stashes().push(name);
        }
    }

    /**
     * Deletes {@code stash}.
     */
    record Drop(net.vanfleteren.jazygit.core.repo.Stash stash) implements StashCmd {

        @Override
        public Operation operation() {
            return STASH_DROP;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.stashes().drop(stash);
        }
    }

    /**
     * Applies {@code stash} and drops it.
     */
    record Pop(net.vanfleteren.jazygit.core.repo.Stash stash) implements StashCmd {

        @Override
        public Operation operation() {
            return STASH_POP;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.stashes().pop(stash);
        }
    }
}
