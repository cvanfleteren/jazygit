package net.vanfleteren.jazygit.features.branches.branch.pull;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.PullMode;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface PullMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PullUpdate.update(model, this);
    }

    /**
     * The user asked to pull a branch.
     */
    record Requested(BranchName branch) implements PullMsg {}

    /**
     * Git said whether {@code pull.rebase} or {@code pull.ff} is set: {@code configured}, for {@code branch}, which has
     * diverged from its upstream.
     */
    record Checked(BranchName branch, boolean configured) implements PullMsg {}

    /**
     * Asking whether {@code pull.rebase} or {@code pull.ff} is set failed.
     */
    record CheckFailed(BranchName branch, Failure failure) implements PullMsg {}

    /**
     * The pull {@code cmd}, which ran {@code commands}, pulled nothing: the branch has diverged from its upstream, as
     * the fetch of the pull found out, and nothing says how to pull it.
     */
    record NeedsStrategy(PullCmd.Pull cmd, List<ExecutedCommand> commands) implements PullMsg {}

    /**
     * The user chose how to pull the diverged branch.
     */
    record Chosen(PullMode mode) implements PullMsg {}
}
