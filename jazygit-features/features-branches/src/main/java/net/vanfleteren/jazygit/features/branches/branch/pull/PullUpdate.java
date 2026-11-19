package net.vanfleteren.jazygit.features.branches.branch.pull;

import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.Branches;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd.FastForward;
import net.vanfleteren.jazygit.features.branches.branch.pull.PullCmd.Pull;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.UpstreamError;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Pulling a branch, as lazygit does: the checked out branch is pulled, any other one is fast-forwarded to its
 * upstream.
 */
public final class PullUpdate {

    private PullUpdate() {}

    public static Next update(Model model, PullMsg msg) {
        return switch (msg) {
            case PullMsg.Requested(BranchName branch) -> requested(model, branch);
        };
    }

    private static Next requested(Model model, BranchName branch) {
        // A branch that is busy is not pulled meanwhile. A branch without an upstream, or whose upstream was
        // deleted, has nothing to pull from.
        // A branch that is not checked out can only be fast-forwarded, which its own commits would prevent, as
        // would another worktree that has it checked out.
        return model.idleBranch(branch)
                .map(known -> known.upstream()
                        .map(upstream -> known.current()
                                ? OperationUpdate.start(model.clearError(), new Pull(branch))
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
}
