package net.vanfleteren.jazygit.features.branches.branch.pull;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Asks, before the diverged {@code branch} is pulled, whether {@code pull.rebase} or {@code pull.ff} says how: such a
 * pull is refused otherwise, so the user is asked instead.
 */
public record PullCheck(BranchName branch) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .remotes()
                .pullConfigured(branch)
                .fold(error -> failed(Failure.of(error)), configured -> new PullMsg.Checked(branch, configured));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullMsg.CheckFailed(branch, failure);
    }
}
