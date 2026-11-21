package net.vanfleteren.jazygit.features.branches.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.branches.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Adds a worktree at {@code location} that checks out what {@code source} says, as the source adds it: a branch
 * fast-forwarded first when the source says so, or a pull request that gh checks out in it (ADR 169). Once added, it
 * tells where the worktree is, as git lists it.
 *
 * @param switching whether the app switches to it once it is added, without asking (ADR 68)
 */
public record AddWorktreeCmd(WorktreeSource source, String location, boolean switching) implements OperationCmd {

    public static final Operation ADD_WORKTREE =
            Operation.of("branches.log.title.addWorktree", "branches.error.addWorktree.failed");

    /**
     * Adds it; once added, switching to it is offered.
     */
    public AddWorktreeCmd(WorktreeSource source, String location) {
        this(source, location, false);
    }

    @Override
    public Operation operation() {
        return ADD_WORKTREE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(source.label());
    }

    @Override
    public Option<BranchRef> busyBranch() {
        return source.busyBranch();
    }

    @Override
    public Option<Guard> guard() {
        return source.guard();
    }

    @Override
    public Msg run(Env env) {
        return source.add(env, WorktreeCmd.path(location)).fold(this::failed, commands -> added(env, commands));
    }

    /**
     * Where the worktree was added: {@code location} taken relative to the working tree, with its links
     * resolved, as git lists it.
     */
    private Msg added(Env env, List<ExecutedCommand> commands) {
        Path added = env.git().worktree().resolve(WorktreeCmd.path(location)).normalize();
        return new AddWorktreeMsg.Added(
                this, Try.of(added::toRealPath).getOrElse(added).toString(), commands);
    }
}
