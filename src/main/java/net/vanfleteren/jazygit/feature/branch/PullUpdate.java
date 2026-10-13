package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.FastForward;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Pull;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Pulling a branch, as lazygit does: the checked out branch is pulled, any other one is fast-forwarded to its
 * upstream.
 */
public final class PullUpdate {

    private PullUpdate() {
    }

    public static Next update(Model model, PullMsg msg) {
        return switch (msg) {
            case PullMsg.Requested(String branch) -> requested(model, branch);
        };
    }

    private static Next requested(Model model, String branch) {
        // A branch that is busy is not pulled meanwhile. A branch without an upstream has nothing to pull from.
        // A branch that is not checked out can only be fast-forwarded, which its own commits would prevent.
        return model.branch(branch)
                .filter(known -> !model.busy().contains(branch))
                .map(known -> known.upstream().isEmpty()
                        ? Next.of(model.fail(new AppError.NoUpstream(branch)))
                        : known.current()
                        ? Update.start(model.clearError(), new Pull(branch))
                        : known.ahead() > 0
                        ? Next.of(model.fail(new AppError.UnpushedCommits(branch)))
                        : Update.start(model.clearError(), new FastForward(branch)))
                .getOrElse(() -> Next.of(model));
    }
}
