package net.vanfleteren.jazygit.feature.commit.reset;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Moves the checked out branch to {@code target}, doing with the changes {@code mode} says.
 */
public record ResetCmd(ResetTarget target, ResetMode mode) implements GitOperationCmd {

    public static final Operation RESET = Operation.of("log.title.reset", "error.reset.failed");

    @Override
    public Operation operation() {
        return RESET;
    }

    @Override
    public Option<String> subject() {
        return Option.of(target.label());
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.reset(target, mode);
    }
}
