package net.vanfleteren.jazygit.feature.forcedelete;

import net.vanfleteren.jazygit.feature.forcedelete.ForceDeleteMsg.Confirmed;
import net.vanfleteren.jazygit.feature.forcedelete.ForceDeleteMsg.NotFullyMerged;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Deleting a local branch that git refused to delete as it is not merged, once the user agreed to lose its commits.
 */
public final class ForceDeleteUpdate {

    private ForceDeleteUpdate() {}

    public static Next update(Model model, ForceDeleteMsg msg) {
        return switch (msg) {
            // The deletion ran, and was refused; deleting it anyway is offered instead of the failure. Whether its
            // upstream is gone is as the model had it before: a remote branch deleted along with it does not count.
            case NotFullyMerged(var cmd, var branch, int lost, var commands) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .openPopupIfFree(new ForceDeleteBranchPopup(
                                branch,
                                lost,
                                model.repo()
                                        .branch(branch)
                                        .flatMap(Branch::goneUpstream)
                                        .isDefined())));
            // Only a branch that is still there, not checked out here and that nothing runs on is deleted. One
            // checked out in another worktree is left to git to refuse: after removing a worktree the model still
            // lists the branch there until the refresh lands.
            case Confirmed() ->
                Update.confirm(
                        model,
                        ForceDeleteBranchPopup.class,
                        popup -> model.idleBranch(popup.branch())
                                .filter(branch -> !branch.current())
                                .map(branch -> new ForceDeleteCmd(branch.name())));
        };
    }
}
