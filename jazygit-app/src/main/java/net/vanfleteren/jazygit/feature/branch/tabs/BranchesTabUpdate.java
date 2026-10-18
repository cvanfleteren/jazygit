package net.vanfleteren.jazygit.feature.branch.tabs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg.Chosen;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg.Switched;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching the branches pane between the local branches, the remote ones and the worktrees. Switching closes the
 * divergence listed in place of the local branches.
 */
public final class BranchesTabUpdate {

    private BranchesTabUpdate() {}

    public static Next update(Model model, BranchesTabMsg msg) {
        return switch (msg) {
            case Switched(int by) ->
                switched(model, model.repoView().branchesTab().shifted(by));
            case Chosen(BranchesTab tab) -> switched(model, tab);
        };
    }

    private static Next switched(Model model, BranchesTab tab) {
        return Next.of(
                model.mapRepoView(repoView -> repoView.withBranchesTab(tab).withDivergence(Option.none())));
    }
}
