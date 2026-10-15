package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commands that create, apply and delete stashes.
 */
public sealed interface StashCmd extends GitOperationCmd {

    /**
     * Stashes the changes under {@code name}.
     */
    record Stash(Option<String> name) implements StashCmd {

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
     * Deletes {@code stash}.
     */
    record Drop(net.vanfleteren.jazygit.git.model.Stash stash) implements StashCmd {

        @Override
        public Operation operation() {
            return Operation.STASH_DROP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.drop(stash);
        }
    }

    /**
     * Applies {@code stash} and drops it.
     */
    record Pop(net.vanfleteren.jazygit.git.model.Stash stash) implements StashCmd {

        @Override
        public Operation operation() {
            return Operation.STASH_POP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.pop(stash);
        }
    }
}
