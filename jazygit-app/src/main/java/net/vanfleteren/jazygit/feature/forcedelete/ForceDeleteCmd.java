package net.vanfleteren.jazygit.feature.forcedelete;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

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
    public static Msg refused(OperationCmd cmd, BranchName branch, GitFailure failure, Git git) {
        return failure.error() instanceof GitError.NotFullyMerged
                ? git.branches()
                        .countOnlyOn(branch)
                        .fold(
                                uncounted -> cmd.failed(Failure.of(failure)),
                                lost -> forceIfNothingLost(cmd, branch, failure, git, lost))
                : cmd.failed(Failure.of(failure));
    }

    /**
     * What {@code cmd} reports once git refused to delete {@code branch} with {@code failure}, as {@code lost} commits
     * are only on it: with none, it is deleted with force; else whether to lose them is asked.
     */
    private static Msg forceIfNothingLost(OperationCmd cmd, BranchName branch, GitFailure failure, Git git, int lost) {
        return lost == 0
                ? git.branches()
                        .forceDeleteLocal(branch)
                        .fold(
                                forced -> cmd.failed(Failure.of(forced.after(failure.commands()))),
                                forced -> cmd.succeeded(failure.commands().appendAll(forced)))
                : new ForceDeleteMsg.NotFullyMerged(cmd, branch, lost, failure.commands());
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
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.branches().forceDeleteLocal(branch);
    }
}
