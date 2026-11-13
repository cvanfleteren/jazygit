package net.vanfleteren.jazygit.feature.branch.merge;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Merges {@code branch} into the checked out branch.
 */
public record MergeCmd(BranchName branch, MergeMode mode) implements BranchCmd {

    public static final Operation MERGE =
            Operation.of("log.title.merge", "error.merge.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return MERGE;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.integrate().merge(branch, mode);
    }
}
