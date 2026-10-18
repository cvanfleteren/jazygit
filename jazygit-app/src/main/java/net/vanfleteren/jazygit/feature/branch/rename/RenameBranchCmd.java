package net.vanfleteren.jazygit.feature.branch.rename;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Renames {@code branch} to {@code newName}.
 */
public record RenameBranchCmd(Branch.Name branch, Branch.Name newName) implements BranchCmd {

    public static final Operation RENAME_BRANCH = Operation.of("log.title.renameBranch", "error.renameBranch.failed");

    @Override
    public Operation operation() {
        return RENAME_BRANCH;
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.renameBranch(branch, newName);
    }
}
