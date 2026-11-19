package net.vanfleteren.jazygit.features.branches.worktree.prune;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Forgets the worktrees whose directory is gone.
 */
public record PruneWorktreesCmd() implements GitOperationCmd {

    public static final Operation PRUNE_WORKTREES =
            Operation.of("branches.log.title.pruneWorktrees", "branches.error.pruneWorktrees.failed");

    @Override
    public Operation operation() {
        return PRUNE_WORKTREES;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.worktrees().prune();
    }
}
