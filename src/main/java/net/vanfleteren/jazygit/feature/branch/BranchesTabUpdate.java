package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchesTabMsg.Switched;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching the branches pane between the local and the remote branches.
 */
public final class BranchesTabUpdate {

    private BranchesTabUpdate() {
    }

    public static Next update(Model model, BranchesTabMsg msg) {
        return switch (msg) {
            case Switched() -> Next.of(model.withBranchesTab(model.branchesTab().other()));
        };
    }
}
