package net.vanfleteren.jazygit.features.changes.commit.reset;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.ResetMode;
import net.vanfleteren.jazygit.core.git.model.ResetTarget;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Moves the checked out branch to {@code target}, doing with the changes {@code mode} says.
 */
public record ResetCmd(ResetTarget target, ResetMode mode) implements GitOperationCmd {

    public static final Operation RESET = Operation.of("changes.log.title.reset", "changes.error.reset.failed");

    @Override
    public Operation operation() {
        return RESET;
    }

    @Override
    public Option<String> subject() {
        return Option.of(target.label());
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.commits().reset(target, mode);
    }
}
