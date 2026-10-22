package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Removes the worktree at {@code location}; with {@code force}, also when it has changes. When it has changes and
 * this is not forced, the user is asked to force it instead of being told it failed. Once it is removed, the branch
 * it had checked out is deleted locally when it is {@code deleting}, which is busy meanwhile. When
 * {@code unlocking}, it is unlocked first, as git does not remove a locked worktree.
 */
public record RemoveWorktreeCmd(String location, boolean force, Option<Branch.Name> deleting, boolean unlocking)
        implements WorktreeCmd {

    public static final Operation REMOVE_WORKTREE =
            Operation.of("log.title.removeWorktree", "error.removeWorktree.failed");
    public static final Operation REMOVE_WORKTREE_AND_BRANCH =
            Operation.of("log.title.removeWorktreeAndBranch", "error.removeWorktreeAndBranch.failed");

    /**
     * Removes the worktree at {@code location}, keeping its branch.
     */
    public RemoveWorktreeCmd(String location, boolean force) {
        this(location, force, Option.none());
    }

    /**
     * Removes the worktree at {@code location}, which is not locked, deleting {@code deleting} with it if given.
     */
    public RemoveWorktreeCmd(String location, boolean force, Option<Branch.Name> deleting) {
        this(location, force, deleting, false);
    }

    /**
     * The same removal, forced. It is only asked for once removing failed, so a worktree it unlocks is unlocked by
     * then.
     */
    public RemoveWorktreeCmd forced() {
        return new RemoveWorktreeCmd(location, true, deleting);
    }

    @Override
    public Operation operation() {
        return deleting.isDefined() ? REMOVE_WORKTREE_AND_BRANCH : REMOVE_WORKTREE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(location);
    }

    @Override
    public Option<BranchRef> busyBranch() {
        return deleting.map(BranchRef.Local::new);
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return removing(git);
    }

    @Override
    public Msg run(Env env) {
        return removing(env.git())
                .fold(
                        failure -> failure.error() instanceof GitError.WorktreeHasChanges
                                ? new RemoveWorktreeMsg.NeedsForce(this, failure.commands())
                                : failedWith(failure),
                        this::succeeded);
    }

    private Either<GitFailure<GitError>, List<ExecutedCommand>> removing(GitInfoProvider git) {
        return GitFailure.sequence(
                () -> unlocking
                        ? GitFailure.widen(git.lockWorktree(WorktreeCmd.path(location), false))
                        : GitFailure.nothing(),
                () -> git.removeWorktree(WorktreeCmd.path(location), force),
                () -> deleting.map(branch ->
                                GitFailure.<GitError, List<ExecutedCommand>>widen(git.deleteLocalBranch(branch)))
                        .getOrElse(GitFailure::nothing));
    }
}
