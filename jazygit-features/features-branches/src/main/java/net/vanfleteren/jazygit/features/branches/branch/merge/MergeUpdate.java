package net.vanfleteren.jazygit.features.branches.branch.merge;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.MergeMode;
import net.vanfleteren.jazygit.features.branches.branch.Branches;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Merging a branch into the checked out one.
 */
public final class MergeUpdate {

    private MergeUpdate() {}

    public static Next update(Model model, MergeMsg msg) {
        return switch (msg) {
            case MergeMsg.Requested(BranchName branch) -> requested(model, branch);
            case MergeMsg.Chosen(MergeMode mode) ->
                PopupUpdate.confirm(model, MergePopup.class, popup -> Option.of(new MergeCmd(popup.branch(), mode)));
        };
    }

    /**
     * Only a known branch other than the checked out one can be merged, and only into a checked out branch.
     */
    private static Next requested(Model model, BranchName branch) {
        return PopupUpdate.ask(
                model, Branches.checkedOutAnd(model, branch).map(both -> new MergePopup(both._1, both._2)));
    }
}
