package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Checks {@code branch} out in a new worktree at {@code location}. The branch is busy meanwhile.
 */
public record AddWorktreeCmd(Branch.Name branch, String location) implements WorktreeCmd {

    public static final Operation ADD_WORKTREE = Operation.of("log.title.addWorktree", "error.addWorktree.failed");

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
        return git.addWorktree(WorktreeCmd.path(location), branch);
    }

    @Override
    public Msg succeeded(List<ExecutedCommand> commands) {
        return new AddWorktreeMsg.Added(this, commands);
    }
}
