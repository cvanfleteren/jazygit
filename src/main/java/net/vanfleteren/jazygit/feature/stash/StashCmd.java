package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.process.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.GitOperationCmd;

/**
 * Commands that create, apply and delete stashes.
 */
public sealed interface StashCmd extends GitOperationCmd {

    /**
     * Stashes the changes under {@code name}.
     */
    record Stash(String name) implements StashCmd {

        @Override
        public Operation operation() {
            return Operation.STASH;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.stash(name);
        }
    }

    /**
     * Deletes the stash {@code ref}.
     */
    record Drop(String ref) implements StashCmd {

        @Override
        public Operation operation() {
            return Operation.STASH_DROP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.drop(ref);
        }
    }

    /**
     * Applies the stash {@code ref} and drops it.
     */
    record Pop(String ref) implements StashCmd {

        @Override
        public Operation operation() {
            return Operation.STASH_POP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pop(ref);
        }
    }
}
