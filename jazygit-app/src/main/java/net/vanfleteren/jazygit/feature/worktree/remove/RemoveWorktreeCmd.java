package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Removes the worktree at {@code location}; with {@code force}, also when it has changes. When it has changes and
 * this is not forced, the user is asked to force it instead of being told it failed.
 */
public record RemoveWorktreeCmd(String location, boolean force) implements WorktreeCmd {

    public static final Operation REMOVE_WORKTREE =
            Operation.of("log.title.removeWorktree", "error.removeWorktree.failed");

    @Override
    public Operation operation() {
        return REMOVE_WORKTREE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(location);
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.removeWorktree(WorktreeCmd.path(location), force);
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .removeWorktree(WorktreeCmd.path(location), force)
                .fold(
                        failure -> failure.error() instanceof GitError.WorktreeHasChanges
                                ? new RemoveWorktreeMsg.NeedsForce(this, failure.commands())
                                : failedWith(failure),
                        this::succeeded);
    }
}
