package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Creates the branch {@code branch} at {@code base} and switches to it.
 */
public record NewBranchCmd(Branch.Name branch, String base) implements BranchCmd.ChecksOutBranch {

    public static final Operation NEW_BRANCH = Operation.of("log.title.newBranch", "error.newBranch.failed");

    @Override
    public Operation operation() {
        return NEW_BRANCH;
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.checkoutNewBranch(branch, base);
    }
}
