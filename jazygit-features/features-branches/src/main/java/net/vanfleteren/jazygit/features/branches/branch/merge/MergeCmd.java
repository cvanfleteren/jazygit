package net.vanfleteren.jazygit.features.branches.branch.merge;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Merges {@code branch}, a local or a remote-tracking one, into the checked out branch.
 */
public record MergeCmd(BranchRef branch, MergeMode mode) implements GitOperationCmd {

    public static final Operation MERGE = Operation.of("branches.log.title.merge", "branches.error.merge.failed")
            .waitingForUnfinished();

    @Override
    public Option<String> subject() {
        return Option.of(branch.name());
    }

    @Override
    public Operation operation() {
        return MERGE;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.integrate().merge(branch, mode);
    }
}
