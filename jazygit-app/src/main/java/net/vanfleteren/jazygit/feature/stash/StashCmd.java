package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commands that create, apply and delete stashes.
 */
public sealed interface StashCmd extends GitOperationCmd {

    Operation STASH = Operation.of("log.title.stash", "error.stash.failed");

    Operation STASH_POP = Operation.of("log.title.stashPop", "error.stashPop.failed");

    Operation STASH_DROP = Operation.of("log.title.stashDrop", "error.stashDrop.failed");

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
    record Drop(net.vanfleteren.jazygit.git.model.Stash stash) implements StashCmd {

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
    record Pop(net.vanfleteren.jazygit.git.model.Stash stash) implements StashCmd {

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
