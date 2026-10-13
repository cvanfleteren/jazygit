package net.vanfleteren.jazygit.feature.discard;

import net.vanfleteren.jazygit.git.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Commands that throw changes away.
 */
public sealed interface DiscardCmd extends OperationCmd {

    record Discard(DiscardPlan plan) implements DiscardCmd {

        @Override
        public Operation operation() {
            return Operation.DISCARD;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.discard(plan);
        }
    }
}
