package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.DeleteScope;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Chosen;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Deleting a branch.
 */
public final class DeleteBranchUpdate {

    private DeleteBranchUpdate() {
    }

    public static Next update(Model model, DeleteBranchMsg msg) {
        return switch (msg) {
            case Requested(String branch) -> requested(model, branch);
            case Cancelled() -> Next.of(model.withoutPopup(DeleteBranchPopup.class));
            case Chosen(DeleteScope scope) -> chosen(model, scope);
        };
    }

    /**
     * Only a known branch that is not checked out can be deleted, and not while it is being pushed or deleted.
     */
    private static Next requested(Model model, String branch) {
        boolean deletable = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.exists(b -> b.name().equals(branch) && !b.current())
                && !model.busy().contains(branch);
        return deletable ? Next.of(model.openPopup(new DeleteBranchPopup(branch))) : Next.of(model);
    }

    private static Next chosen(Model model, DeleteScope scope) {
        return model.popup(DeleteBranchPopup.class).map(DeleteBranchPopup::branch)
                // Deleting on the remote is not possible for a local-only branch, nor for the default one.
                .filter(branch -> scope == DeleteScope.LOCAL || remoteDeletable(model, branch))
                .map(branch -> Next.of(model.withoutPopup(DeleteBranchPopup.class).withBusyBranch(branch)
                                .clearError(),
                        new DeleteBranch(branch, scope)))
                .getOrElse(() -> Next.of(model));
    }

    private static boolean remoteDeletable(Model model, String branch) {
        return model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.exists(b -> b.name().equals(branch) && b.remoteDeletable());
    }
}
