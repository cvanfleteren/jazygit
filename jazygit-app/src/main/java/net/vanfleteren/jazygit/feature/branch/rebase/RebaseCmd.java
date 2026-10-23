package net.vanfleteren.jazygit.feature.branch.rebase;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Rebases the checked out branch onto {@code branch}.
 */
public record RebaseCmd(BranchName branch) implements BranchCmd {

    public static final Operation REBASE =
            Operation.of("log.title.rebase", "error.rebase.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return REBASE;
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.rebase(branch);
    }
}
