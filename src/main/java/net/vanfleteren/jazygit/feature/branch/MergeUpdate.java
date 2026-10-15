package net.vanfleteren.jazygit.feature.branch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Merge;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Merging a branch into the checked out one.
 */
public final class MergeUpdate {

    private MergeUpdate() {
    }

    public static Next update(Model model, MergeMsg msg) {
        return switch (msg) {
            case MergeMsg.Requested(Branch.Name branch) -> requested(model, branch);
            case MergeMsg.Cancelled() -> Next.of(model.withoutPopup(MergePopup.class));
            case MergeMsg.Chosen(MergeMode mode) ->
                    Update.confirm(model, MergePopup.class, popup -> Option.of(new Merge(popup.branch(), mode)));
        };
    }

    /**
     * Only a known branch other than the checked out one can be merged, and only into a checked out branch.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return Update.ask(model, Branches.checkedOutAnd(model, branch).map(both -> new MergePopup(both._1, both._2)));
    }
}
