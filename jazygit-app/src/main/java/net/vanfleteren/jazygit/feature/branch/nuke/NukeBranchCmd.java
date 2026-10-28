package net.vanfleteren.jazygit.feature.branch.nuke;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Nukes {@code branch} (ADR 43): removes the worktree at {@code worktree} whatever it has, deletes the branch with
 * force, and deletes {@code remote} on its remote. A step that fails skips the steps that need it, not the others:
 * the branch is only deleted once its worktree is gone. What is not deleted is reported together.
 *
 * @param worktree the location of the other worktree that has it checked out, if one does
 * @param remote   its remote branch to delete, if any
 */
public record NukeBranchCmd(BranchName branch, Option<String> worktree, Option<RemoteBranch> remote)
        implements BranchCmd.KeepsBranchBusy {

    public static final Operation NUKE_BRANCH = Operation.of("log.title.nukeBranch", "error.nukeBranch.failed");

    @Override
    public Operation operation() {
        return NUKE_BRANCH;
    }

    @Override
    public Option<Guard> guard() {
        return remote.map(target -> Guard.REMOTE_REFS);
    }

    /**
     * The commands that ran, or the first step that failed with all the commands that ran.
     */
    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        List<Step> steps = nuking(git);
        List<ExecutedCommand> commands = steps.flatMap(Step::commands);
        return steps.flatMap(Step::failure)
                .headOption()
                .<Either<GitFailure<ProcessError>, List<ExecutedCommand>>>map(
                        failure -> Either.left(new GitFailure<>(failure.error(), commands, failure.cleanupFailed())))
                .getOrElse(() -> Either.right(commands));
    }

    @Override
    public Msg run(Env env) {
        List<Step> steps = nuking(env.git());
        List<ExecutedCommand> commands = steps.flatMap(Step::commands);
        return steps.forAll(Step::deleted)
                ? succeeded(commands)
                : new NukeBranchMsg.NotAllNuked(
                        this,
                        commands,
                        steps.reject(Step::deleted).map(Step::what),
                        steps.flatMap(Step::failure).map(Failure::of));
    }

    private List<Step> nuking(GitInfoProvider git) {
        Option<Step> removed = worktree.map(
                location -> new Step(location, Option.of(git.removeWorktreeAnyway(WorktreeCmd.path(location)))));
        Step deleted = new Step(
                branch.value(), Option.when(removed.forAll(Step::deleted), () -> git.forceDeleteLocalBranch(branch)));
        Option<Step> deletedRemote =
                remote.map(target -> new Step(target.shortName(), Option.of(git.deleteRemoteBranch(target))));
        return removed.toList().append(deleted).appendAll(deletedRemote);
    }

    /**
     * A step of the nuke: what it deletes, named as the dialog named it, and how that went; none when it was
     * skipped, as a step it needs failed.
     */
    private record Step(String what, Option<Either<GitFailure<ProcessError>, List<ExecutedCommand>>> outcome) {

        boolean deleted() {
            return outcome.exists(Either::isRight);
        }

        List<ExecutedCommand> commands() {
            return outcome.map(done -> done.fold(GitFailure::commands, ran -> ran))
                    .getOrElse(List.empty());
        }

        Option<GitFailure<ProcessError>> failure() {
            return outcome.flatMap(done -> done.swap().toOption());
        }
    }
}
