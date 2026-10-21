package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Checks {@code branch} out in a new worktree at {@code location}, creating it first when it is {@code creating}.
 * The branch is busy meanwhile. Once added, it tells where the worktree is, as git lists it.
 */
public record AddWorktreeCmd(Branch.Name branch, String location, Option<NewBranch> creating) implements WorktreeCmd {

    public static final Operation ADD_WORKTREE = Operation.of("log.title.addWorktree", "error.addWorktree.failed");

    /**
     * Checks {@code branch}, which exists, out in a new worktree at {@code location}.
     */
    public AddWorktreeCmd(Branch.Name branch, String location) {
        this(branch, location, Option.none());
    }

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
        Path path = WorktreeCmd.path(location);
        return creating.fold(
                () -> git.addWorktree(path, branch),
                created ->
                        git.addWorktreeWithNewBranch(path, branch, created.start(), created.parent(), created.track()));
    }

    @Override
    public Msg run(Env env) {
        return perform(env.git())
                .fold(this::failedWith, commands -> new AddWorktreeMsg.Added(this, added(env), commands));
    }

    /**
     * Where the worktree was added: {@code location} taken relative to the working tree, with its links
     * resolved, as git lists it.
     */
    private String added(Env env) {
        Path added = env.git().workTree().resolve(WorktreeCmd.path(location)).normalize();
        return Try.of(added::toRealPath).getOrElse(added).toString();
    }
}
