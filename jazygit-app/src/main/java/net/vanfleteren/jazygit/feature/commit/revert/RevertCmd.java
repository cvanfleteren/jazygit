package net.vanfleteren.jazygit.feature.commit.revert;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commits the inverse of {@code commit} on top of the checked out branch.
 */
public record RevertCmd(Commit commit) implements GitOperationCmd {

    public static final Operation REVERT =
            Operation.of("log.title.revert", "error.revert.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return REVERT;
    }

    @Override
    public Option<String> subject() {
        return Option.of(commit.shortSha());
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.revert(commit);
    }
}
