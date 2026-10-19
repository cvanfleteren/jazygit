package net.vanfleteren.jazygit.feature.branch.rename;

import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.Requested;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Renaming a local branch.
 */
public final class RenameBranchUpdate {

    private RenameBranchUpdate() {}

    public static Next update(Model model, RenameBranchMsg msg) {
        return switch (msg) {
            case Requested(Branch.Name branch) ->
                Update.ask(model, model.idleBranch(branch).map(known -> new RenameBranchPopup(branch)));
            case Cancelled() -> Next.of(model.withoutPopup(RenameBranchPopup.class));
            case Confirmed() -> confirmed(model);
        };
    }

    private static Next confirmed(Model model) {
        // The name it already has leaves it as it is.
        boolean unchanged = model.popup(RenameBranchPopup.class)
                .exists(popup -> Branch.Name.parse(popup.name()).contains(popup.branch()));
        // A name that is no branch name, like a blank one, keeps the dialog open.
        return unchanged
                ? Next.of(model.withoutPopup(RenameBranchPopup.class))
                : Update.confirm(
                        model,
                        RenameBranchPopup.class,
                        popup -> Branch.Name.parse(popup.name())
                                .map(newName -> new RenameBranchCmd(popup.branch(), newName)));
    }
}
