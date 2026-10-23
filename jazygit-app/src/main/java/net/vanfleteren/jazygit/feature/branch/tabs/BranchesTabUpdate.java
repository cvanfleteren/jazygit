package net.vanfleteren.jazygit.feature.branch.tabs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg.Chosen;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg.Switched;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Switching the branches pane between the local branches, the remote ones and the worktrees. Switching closes the
 * divergence listed in place of the local branches, and the commits of a branch listed in place of the branches.
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

    /**
     * Shows {@code tab}; for the worktrees, reads which have changes right away rather than at the next tick.
     */
    private static Next switched(Model model, BranchesTab tab) {
        return Update.readWorktreeChanges(model.mapRepoView(repoView ->
                repoView.withBranchesTab(tab).withDivergence(Option.none()).withBranchCommits(Option.none())));
    }
}
