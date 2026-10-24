package net.vanfleteren.jazygit.feature.forcedelete;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Deletes the local branch {@code branch}, merged or not, once the user agreed to lose the commits only it has.
 */
public record ForceDeleteCmd(BranchName branch) implements GitOperationCmd {

    public static final Operation FORCE_DELETE_BRANCH =
            Operation.of("log.title.forceDeleteBranch", "error.deleteBranch.failed");

    /**
     * What {@code cmd} reports once deleting {@code branch} locally failed with {@code failure}. When git refused as
     * the branch is not merged, the commits only it has are counted: with none, every commit is on another ref, so
     * it is deleted with force straight away; else whether to lose them is asked. Any other failure, and one where
     * the commits cannot be counted, is reported as it is.
     */
    public static Msg refused(OperationCmd cmd, BranchName branch, GitFailure<?> failure, GitInfoProvider git) {
        return failure.error() instanceof GitError.NotFullyMerged
                ? git.countOnlyOn(branch)
                        .fold(
                                uncounted -> cmd.failedWith(failure),
                                lost -> lost == 0
                                        ? git.forceDeleteLocalBranch(branch)
                                                .fold(
                                                        forced -> cmd.failedWith(forced.after(failure.commands())),
                                                        forced -> cmd.succeeded(failure.commands()
                                                                .appendAll(forced)))
                                        : new ForceDeleteMsg.NotFullyMerged(cmd, branch, lost, failure.commands()))
                : cmd.failedWith(failure);
    }

    @Override
    public Operation operation() {
        return FORCE_DELETE_BRANCH;
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
        return git.forceDeleteLocalBranch(branch);
    }
}
