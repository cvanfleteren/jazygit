package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Requested;

import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Failed;

import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Deleted;

import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Chosen;

import net.vanfleteren.jazygit.feature.branch.DeleteBranchMsg.Cancelled;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.DeleteBranch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Update.Next;
import java.util.List;
import java.util.Optional;

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
            case Chosen(DeleteBranch.DeleteScope scope) -> chosen(model, scope);
            case Deleted(String branch) -> Update.refresh(model.withError(Optional.empty()));
            case Failed(String branch, String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.deleteBranch.failed", branch, message))));
        };
    }

    /**
     * Only a known branch that is not checked out can be deleted.
     */
    private static Next requested(Model model, String branch) {
        boolean deletable = model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch) && !b.current());
        return deletable ? Next.of(model.openPopup(new DeleteBranchPopup(branch))) : Next.of(model);
    }

    private static Next chosen(Model model, DeleteBranch.DeleteScope scope) {
        return model.popup(DeleteBranchPopup.class).map(DeleteBranchPopup::branch)
                // Deleting on the remote is not possible for a local-only branch, nor for the default one.
                .filter(branch -> scope == DeleteBranch.DeleteScope.LOCAL || remoteDeletable(model, branch))
                .map(branch -> Next.of(model.withoutPopup(DeleteBranchPopup.class).withError(Optional.empty()),
                        new DeleteBranch(branch, scope)))
                .orElseGet(() -> Next.of(model));
    }

    private static boolean remoteDeletable(Model model, String branch) {
        return model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(branch) && b.remoteDeletable());
    }
}
