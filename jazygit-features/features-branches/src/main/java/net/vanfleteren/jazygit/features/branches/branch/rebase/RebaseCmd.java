package net.vanfleteren.jazygit.features.branches.branch.rebase;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Rebases the checked out branch onto {@code branch}.
 */
public record RebaseCmd(BranchName branch) implements BranchCmd {

    public static final Operation REBASE = Operation.of("branches.log.title.rebase", "branches.error.rebase.failed")
            .waitingForUnfinished();

    @Override
    public Operation operation() {
        return REBASE;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.integrate().rebase(branch);
    }
}
