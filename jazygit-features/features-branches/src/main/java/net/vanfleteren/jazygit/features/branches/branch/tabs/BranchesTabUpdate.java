package net.vanfleteren.jazygit.features.branches.branch.tabs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.branches.branch.tabs.BranchesTabMsg.Chosen;
import net.vanfleteren.jazygit.features.branches.branch.tabs.BranchesTabMsg.Switched;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.RereadUpdate;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;

/**
 * Switching the branches pane between the local branches, the remote ones and the worktrees. Switching closes the
 * divergence listed in place of the local branches, and the commits of a branch listed in place of the branches.
 */
public final class BranchesTabUpdate {

    private BranchesTabUpdate() {}

    public static Next update(Model model, BranchesTabMsg msg) {
        return switch (msg) {
            case Switched(int by) ->
                switched(model, model.repoView().branches().tab().shifted(by));
            case Chosen(BranchesTab tab) -> switched(model, tab);
        };
    }

    /**
     * Shows {@code tab}; for the worktrees, reads which have changes right away rather than at the next tick.
     */
    private static Next switched(Model model, BranchesTab tab) {
        return RereadUpdate.readWorktreeChanges(model.mapRepoView(repoView -> repoView.mapBranches(
                branches -> branches.withTab(tab).withDivergence(Option.none()).withBranchCommits(Option.none()))));
    }
}
