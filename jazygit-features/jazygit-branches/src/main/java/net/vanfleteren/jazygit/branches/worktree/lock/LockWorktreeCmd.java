package net.vanfleteren.jazygit.branches.worktree.lock;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.branches.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Locks the worktree at {@code location}, or unlocks it when not {@code lock}.
 */
public record LockWorktreeCmd(String location, boolean lock) implements WorktreeCmd {

    public static final Operation LOCK_WORKTREE =
            Operation.of("branches.log.title.lockWorktree", "branches.error.lockWorktree.failed");
    public static final Operation UNLOCK_WORKTREE =
            Operation.of("branches.log.title.unlockWorktree", "branches.error.unlockWorktree.failed");

    @Override
    public Operation operation() {
        return lock ? LOCK_WORKTREE : UNLOCK_WORKTREE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(location);
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.worktrees().lock(WorktreeCmd.path(location), lock);
    }
}
