package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commands that add and remove worktrees.
 */
public sealed interface WorktreeCmd extends GitOperationCmd {

    Operation ADD_WORKTREE = Operation.of("log.title.addWorktree", "error.addWorktree.failed");

    Operation REMOVE_WORKTREE = Operation.of("log.title.removeWorktree", "error.removeWorktree.failed");

    /**
     * The path {@code location} names: a leading {@code ~} is the home directory, as in a shell.
     */
    static Path path(String location) {
        return location.equals("~") || location.startsWith("~/")
                ? Path.of(System.getProperty("user.home") + location.substring(1))
                : Path.of(location);
    }

    /**
     * Checks {@code branch} out in a new worktree at {@code location}. The branch is busy meanwhile.
     */
    record Add(Branch.Name branch, String location) implements WorktreeCmd {

        @Override
        public Operation operation() {
            return ADD_WORKTREE;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch.value());
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch));
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.addWorktree(path(location), branch);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new WorktreeMsg.Added(this, commands);
        }
    }

    /**
     * Removes the worktree at {@code location}; with {@code force}, also when it has changes. When it has changes
     * and this is not forced, the user is asked to force it instead of being told it failed.
     */
    record Remove(String location, boolean force) implements WorktreeCmd {

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
            return git.removeWorktree(path(location), force);
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .removeWorktree(path(location), force)
                    .fold(
                            failure -> failure.error() instanceof GitError.WorktreeHasChanges
                                    ? new WorktreeMsg.NeedsForce(this, failure.commands())
                                    : failedWith(failure),
                            this::succeeded);
        }
    }
}
