package net.vanfleteren.jazygit.features.branches.panes.branches;

import net.vanfleteren.jazygit.kernel.ui.Slot;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;

/**
 * The tabs the features can add to the branches pane (ADR 185), which knows no feature: every tab given is shown,
 * after the pane's own, the local branches, the remote ones and the worktrees, in the order of the features.
 */
public final class BranchesPane {

    /**
     * The tabs the features add after the worktrees, in the order of the features.
     */
    @SuppressWarnings("unchecked")
    public static final Slot<PaneTab<?>> TABS =
            Slot.of("branches.panes.branches.tabs", (Class<PaneTab<?>>) (Class<?>) PaneTab.class);

    private BranchesPane() {}
}
