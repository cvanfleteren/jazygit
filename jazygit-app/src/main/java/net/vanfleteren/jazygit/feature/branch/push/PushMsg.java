package net.vanfleteren.jazygit.feature.branch.push;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface PushMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PushUpdate.update(model, this);
    }

    /**
     * The user asked to push a branch.
     */
    record Requested(BranchName branch) implements PushMsg {}

    /**
     * The user confirmed the upstream typed for a branch without one, which should be {@code <remote> <branch>}.
     */
    record UpstreamConfirmed() implements PushMsg {}

    /**
     * The user confirmed overwriting the remote branch, which the branch is behind.
     */
    record ForceConfirmed() implements PushMsg {}

    /**
     * Git said whether the branch of {@code popup} ever had the commit its upstream is at: {@code had}.
     */
    record ForceChecked(ForcePushPopup popup, boolean had) implements PushMsg {}

    /**
     * Asking git whether {@code branch} ever had the commit its upstream is at failed.
     */
    record ForceCheckFailed(BranchName branch, Failure failure) implements PushMsg {}

    /**
     * The push {@code cmd}, which ran {@code commands}, pushed nothing: {@code push.default} does not push the branch
     * where it would go, as {@code refused} says.
     */
    record Refused(PushCmd.Push cmd, SetUpstreamPopup.Refusal refused, List<ExecutedCommand> commands)
            implements PushMsg {}
}
