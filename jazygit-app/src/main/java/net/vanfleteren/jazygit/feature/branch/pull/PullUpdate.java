package net.vanfleteren.jazygit.feature.branch.pull;

import net.vanfleteren.jazygit.feature.branch.Branches;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd.FastForward;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd.Pull;
import net.vanfleteren.jazygit.git.model.BranchName;
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
                                ? Update.start(model.clearError(), new Pull(branch))
                                : known.ahead() > 0
                                        ? Next.of(model.fail(new AppError.UnpushedCommits(branch)))
                                        : Branches.checkedOutElsewhere(model, PullCmd.FAST_FORWARD, branch)
                                                .map(error -> Next.of(model.fail(error)))
                                                .getOrElse(() -> Update.start(
                                                        model.clearError(),
                                                        new FastForward(branch, upstream.branch()))))
                        .getOrElse(() -> Next.of(model.fail(AppError.noUpstream(known)))))
                .getOrElse(() -> Next.of(model));
    }
}
