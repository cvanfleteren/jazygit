package net.vanfleteren.jazygit.feature.worktree.lock;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Locks the worktree at {@code location}, or unlocks it when not {@code lock}.
 */
public record LockWorktreeCmd(String location, boolean lock) implements WorktreeCmd {

    public static final Operation LOCK_WORKTREE = Operation.of("log.title.lockWorktree", "error.lockWorktree.failed");
    public static final Operation UNLOCK_WORKTREE =
            Operation.of("log.title.unlockWorktree", "error.unlockWorktree.failed");

    @Override
    public Operation operation() {
        return lock ? LOCK_WORKTREE : UNLOCK_WORKTREE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(location);
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.lockWorktree(WorktreeCmd.path(location), lock);
    }
}
