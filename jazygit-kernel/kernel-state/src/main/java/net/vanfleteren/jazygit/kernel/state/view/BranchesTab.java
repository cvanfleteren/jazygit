package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The branches pane's own tabs (ADR 185), in the order it shows them, before those the features add. The kernel knows
 * them by name, as it reads what they show: the divergence and the builds belong to the local branches, and which
 * worktrees have changes is read while the worktrees are shown. The pane keeps which it shows by its {@link #id()} in
 * {@link RepoView#tabs}, as any pane with tabs does.
 */
public enum BranchesTab {
    /**
     * The local branches.
     */
    LOCAL(new TabId("branches.tab.local")),
    /**
     * The remote-tracking branches.
     */
    REMOTE(new TabId("branches.tab.remote")),
    /**
     * The worktrees of the repository.
     */
    WORKTREES(new TabId("branches.tab.worktrees"));

    private final TabId id;

    BranchesTab(TabId id) {
        this.id = id;
    }

    /**
     * The id the pane keeps this tab shown by.
     */
    public TabId id() {
        return id;
    }

    /**
     * The tab whose id is {@code id}, if it is one of the pane's own rather than a feature's.
     */
    public static Option<BranchesTab> of(TabId id) {
        return List.of(values()).find(tab -> tab.id.equals(id));
    }
}
