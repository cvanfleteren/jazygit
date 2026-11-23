package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;
import net.vanfleteren.jazygit.kernel.state.view.TabId;

/**
 * Where the app shows a repository it opens in place of the one shown: the list of the branches pane, the tab each
 * pane with tabs shows, and the pane that has the focus; and what it does there once it is read, if anything.
 *
 * @param branchesTab the list the branches pane shows
 * @param focusedPane the pane or area of the content panel that has the focus, as {@link RepoView#focusedPane}
 *                    keeps it; none for the files, where the app starts
 * @param then        the message to handle once the status, the branches and the worktrees of the repository are
 *                    read, as when a checkout asked for in another worktree is to run there (ADR 68)
 * @param tabs        the tab each pane with tabs shows, as {@link RepoView#tabs} keeps it (ADR 185)
 */
public record Landing(
        BranchesTab branchesTab, Option<Focus.OnScreen> focusedPane, Option<Msg> then, Map<PaneId, TabId> tabs) {

    /**
     * Where the app starts: the local branches, the files focused.
     */
    public static final Landing START = new Landing(BranchesTab.LOCAL, Option.none());

    /**
     * Showing the list {@code branchesTab}, with the pane {@code focusedPane} focused, and nothing more to do.
     */
    public Landing(BranchesTab branchesTab, Option<Focus.OnScreen> focusedPane) {
        this(branchesTab, focusedPane, Option.none());
    }

    /**
     * Showing the list {@code branchesTab}, with the pane {@code focusedPane} focused, then handling {@code then}; each
     * pane with tabs shows its first.
     */
    public Landing(BranchesTab branchesTab, Option<Focus.OnScreen> focusedPane, Option<Msg> then) {
        this(branchesTab, focusedPane, then, HashMap.empty());
    }

    /**
     * Where {@code view} is: the same list of the branches pane, the same tabs, the same pane focused.
     */
    public static Landing of(RepoView view) {
        return new Landing(view.branches().tab(), view.focusedPane(), Option.none(), view.tabs());
    }

    /**
     * This landing, handling {@code msg} once the repository is read.
     */
    public Landing followedBy(Msg msg) {
        return new Landing(branchesTab, focusedPane, Option.of(msg), tabs);
    }

    /**
     * This landing, with each pane showing the tab it shows in {@code view}: another worktree of the repository opens
     * on the same tabs (ADR 185).
     */
    public Landing keepingTabs(RepoView view) {
        return new Landing(branchesTab, focusedPane, then, view.tabs());
    }

    /**
     * {@code view} shown here.
     */
    public RepoView applyTo(RepoView view) {
        return view.mapBranches(branches -> branches.withTab(branchesTab))
                .withFocusedPane(focusedPane)
                .withTabs(tabs);
    }
}
