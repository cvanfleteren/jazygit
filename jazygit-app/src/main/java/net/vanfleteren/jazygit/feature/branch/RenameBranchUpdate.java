package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.RenameBranch;
import net.vanfleteren.jazygit.feature.branch.RenameBranchMsg.Cancelled;
import net.vanfleteren.jazygit.feature.branch.RenameBranchMsg.Confirmed;
import net.vanfleteren.jazygit.feature.branch.RenameBranchMsg.Requested;
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
            case Confirmed(String name) -> confirmed(model, name);
        };
    }

    private static Next confirmed(Model model, String name) {
        // The name it already has leaves it as it is.
        boolean unchanged = model.popup(RenameBranchPopup.class)
                .exists(popup -> Branch.Name.parse(name).contains(popup.branch()));
        // A name that is no branch name, like a blank one, keeps the dialog open.
        return unchanged
                ? Next.of(model.withoutPopup(RenameBranchPopup.class))
                : Update.confirm(
                        model,
                        RenameBranchPopup.class,
                        popup -> Branch.Name.parse(name).map(newName -> new RenameBranch(popup.branch(), newName)));
    }
}
