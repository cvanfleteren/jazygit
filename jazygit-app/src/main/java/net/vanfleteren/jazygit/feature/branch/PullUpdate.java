package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.FastForward;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Pull;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Pulling a branch, as lazygit does: the checked out branch is pulled, any other one is fast-forwarded to its
 * upstream.
 */
public final class PullUpdate {

    private PullUpdate() {}

    public static Next update(Model model, PullMsg msg) {
        return switch (msg) {
            case PullMsg.Requested(Branch.Name branch) -> requested(model, branch);
        };
    }

    private static Next requested(Model model, Branch.Name branch) {
        // A branch that is busy is not pulled meanwhile. A branch without an upstream, or whose upstream was
        // deleted, has nothing to pull from.
        // A branch that is not checked out can only be fast-forwarded, which its own commits would prevent, as
        // would another worktree that has it checked out.
        return model.idleBranch(branch)
                .map(known -> known.upstream()
                        .map(upstream -> known.current()
                                ? Update.start(model.clearError(), new Pull(branch))
                                : known.ahead() > 0
                                        ? Next.of(model.fail(new AppError.UnpushedCommits(branch)))
                                        : Branches.checkedOutElsewhere(model, BranchCmd.FAST_FORWARD, branch)
                                                .map(error -> Next.of(model.fail(error)))
                                                .getOrElse(() -> Update.start(
                                                        model.clearError(),
                                                        new FastForward(branch, upstream.branch()))))
                        .getOrElse(() -> Next.of(model.fail(known.goneUpstream()
                                .<AppError>map(upstream -> new AppError.UpstreamGone(branch, upstream.shortName()))
                                .getOrElse(() -> new AppError.NoUpstream(branch))))))
                .getOrElse(() -> Next.of(model));
    }
}
