package net.vanfleteren.jazygit.features.branches.branch.tabs;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.RereadUpdate;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What the branches pane does once another of its tabs is shown (ADR 185): it closes the divergence listed in place of
 * the local branches and the commits of a branch listed in place of the branches, and, for the worktrees, reads which
 * have changes right away rather than at the next tick.
 */
public final class BranchesTabUpdate {

    private BranchesTabUpdate() {}

    public static Next reacting(Model model, KernelEvent event) {
        return switch (event) {
            case KernelEvent.TabShown shown when shown.pane().equals(PaneId.BRANCHES) -> shown(model);
            case KernelEvent.TabShown _,
                    KernelEvent.LogRead _,
                    KernelEvent.OperationDone _,
                    KernelEvent.Fetched _,
                    KernelEvent.RepositoryOpened _,
                    KernelEvent.ScreenShown _,
                    KernelEvent.ChangesOpened _,
                    KernelEvent.Switched _,
                    KernelEvent.FileToOpen _,
                    KernelEvent.RemoteChanged _ -> Next.of(model);
        };
    }

    private static Next shown(Model model) {
        return RereadUpdate.readWorktreeChanges(model.mapRepoView(repoView -> repoView.mapBranches(
                branches -> branches.withDivergence(Option.none()).withBranchCommits(Option.none()))));
    }
}
