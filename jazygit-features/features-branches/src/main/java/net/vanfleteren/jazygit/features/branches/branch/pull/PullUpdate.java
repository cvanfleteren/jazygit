package net.vanfleteren.jazygit.features.branches.branch.pull;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.PullMode;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.Branches;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd.FastForward;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd.Pull;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.RereadUpdate;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.UpstreamError;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Pulling a branch, as lazygit does: the checked out branch is pulled, any other one is fast-forwarded to its
 * upstream. A checked out branch that has diverged from its upstream is pulled by rebasing or merging as git's
 * {@code pull.rebase} and {@code pull.ff} say; when neither is set, git refuses, so the user is asked which one.
 */
public final class PullUpdate {

    private PullUpdate() {}

    public static Next update(Model model, PullMsg msg) {
        return switch (msg) {
            case PullMsg.Requested(BranchName branch) -> requested(model, branch);
            case PullMsg.Checked(BranchName branch, boolean configured) -> checked(model, branch, configured);
            case PullMsg.CheckFailed(BranchName branch, Failure failure) ->
                Next.of(model.fail(new AppError.OperationFailed(PullCmd.PULL, Option.of(branch.value()), failure)));
            // The pull ran and pulled nothing; how to pull is asked instead of the failure.
            case PullMsg.NeedsStrategy(var cmd, var commands) ->
                RereadUpdate.refresh(
                        OperationUpdate.done(model, cmd, commands).openPopupIfFree(new PullPopup(cmd.branch())));
            case PullMsg.Chosen(PullMode mode) ->
                PopupUpdate.confirm(model, PullPopup.class, popup -> Option.of(new Pull(popup.branch(), mode)));
        };
    }

    private static Next requested(Model model, BranchName branch) {
        // A branch that is busy is not pulled meanwhile. A branch without an upstream, or whose upstream was
        // deleted, has nothing to pull from.
        // A branch that is not checked out can only be fast-forwarded, which its own commits would prevent, as
        // would another worktree that has it checked out.
        // A checked out branch that has diverged is pulled as the settings say, which only git can be asked.
        return model.idleBranch(branch)
                .map(known -> known.upstream()
                        .map(upstream -> known.current()
                                ? diverged(known)
                                        ? Next.of(model.clearError(), new PullCheck(branch))
                                        : OperationUpdate.start(model.clearError(), new Pull(branch))
                                : known.ahead() > 0
                                        ? Next.of(model.fail(new BranchError.UnpushedCommits(branch)))
                                        : Branches.checkedOutElsewhere(model, PullCmd.FAST_FORWARD, branch)
                                                .map(error -> Next.of(model.fail(error)))
                                                .getOrElse(() -> OperationUpdate.start(
                                                        model.clearError(),
                                                        new FastForward(branch, upstream.branch()))))
                        .getOrElse(() -> Next.of(model.fail(UpstreamError.of(known)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Git said whether the pull of the diverged {@code branch} is settled: it is pulled as it says, or else the user is
     * asked how, unless the user has a popup open meanwhile, which stays. A refresh may have changed the branch while git was asked: one that is no longer a diverged checked
     * out one is handled again.
     */
    private static Next checked(Model model, BranchName branch, boolean configured) {
        return model.idleBranch(branch)
                .filter(known -> known.current() && diverged(known))
                .map(_ -> configured
                        ? OperationUpdate.start(model.clearError(), new Pull(branch))
                        : Next.of(model.clearError().openPopupIfFree(new PullPopup(branch))))
                .getOrElse(() -> requested(model, branch));
    }

    /**
     * Whether {@code branch} has commits of its own and its upstream has others: neither a fast-forward nor nothing.
     */
    private static boolean diverged(Branch branch) {
        return branch.ahead() > 0 && branch.behind() > 0;
    }
}
