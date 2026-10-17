package net.vanfleteren.jazygit.feature.discard;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commands that throw changes away.
 */
public sealed interface DiscardCmd extends GitOperationCmd {

    Operation DISCARD = Operation.of("log.title.discard", "error.discard.failed");

    record Discard(DiscardPlan plan) implements DiscardCmd {

        @Override
        public Operation operation() {
            return DISCARD;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.discard(plan);
        }
    }
}
