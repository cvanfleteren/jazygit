package net.vanfleteren.jazygit.feature.worktree.prune;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Forgets the worktrees whose directory is gone.
 */
public record PruneWorktreesCmd() implements GitOperationCmd {

    public static final Operation PRUNE_WORKTREES =
            Operation.of("log.title.pruneWorktrees", "error.pruneWorktrees.failed");

    @Override
    public Operation operation() {
        return PRUNE_WORKTREES;
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.pruneWorktrees();
    }
}
