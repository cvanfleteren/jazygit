package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Update;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Chosen;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.Branch;
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
        boolean deletable = Branches.isOtherBranch(model, branch) && !model.busy().contains(branch);
        return deletable ? Next.of(model.openPopup(new DeleteBranchPopup(branch))) : Next.of(model);
    }

    private static Next chosen(Model model, DeleteScope scope) {
        // Deleting on the remote is not possible for a local-only branch, nor for the default one.
        return Update.confirm(model, DeleteBranchPopup.class, popup -> Option.of(popup.branch())
                .filter(branch -> scope == DeleteScope.LOCAL || remoteDeletable(model, branch))
                .map(branch -> new DeleteBranch(branch, scope)));
    }

    static boolean remoteDeletable(Model model, String branch) {
        return model.branch(branch).exists(Branch::remoteDeletable);
    }
}
