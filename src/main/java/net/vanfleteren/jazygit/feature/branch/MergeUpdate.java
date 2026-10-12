package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Merge;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Merging a branch into the checked out one.
 */
public final class MergeUpdate {

    private MergeUpdate() {
    }

    public static Next update(Model model, MergeMsg msg) {
        return switch (msg) {
            case MergeMsg.Requested(String branch) -> requested(model, branch);
            case MergeMsg.Cancelled() -> Next.of(model.withoutPopup(MergePopup.class));
            case MergeMsg.Chosen(MergeMode mode) -> model.popup(MergePopup.class).map(MergePopup::branch)
                    .map(branch -> Next.of(model.withoutPopup(MergePopup.class).clearError(),
                            new Merge(branch, mode)))
                    .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Only a known branch other than the checked out one can be merged, and only into a checked out branch.
     */
    private static Next requested(Model model, String branch) {
        if (!(model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches))) {
            return Next.of(model);
        }
        Option<Branch> current = branches.find(Branch::current);
        boolean known = branches.exists(b -> b.name().equals(branch) && !b.current());
        return known && current.isDefined()
                ? Next.of(model.openPopup(new MergePopup(current.get().name(), branch)).clearError())
                : Next.of(model);
    }
}
