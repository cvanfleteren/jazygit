package net.vanfleteren.jazygit.features.changes.discard;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commands that throw changes away.
 */
public sealed interface DiscardCmd extends GitOperationCmd {

    Operation DISCARD = Operation.of("changes.log.title.discard", "changes.error.discard.failed");

    record Discard(DiscardPlan plan) implements DiscardCmd {

        @Override
        public Operation operation() {
            return DISCARD;
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().discard(plan);
        }
    }
}
