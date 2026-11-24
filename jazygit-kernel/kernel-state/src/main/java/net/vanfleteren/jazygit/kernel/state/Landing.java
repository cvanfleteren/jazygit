package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;
import net.vanfleteren.jazygit.kernel.state.view.TabId;

/**
 * Where the app shows a repository it opens in place of the one shown: the tab each pane with tabs shows, among them
 * the list of the branches pane, and the pane that has the focus; and what it does there once it is read, if anything.
 *
 * @param focusedPane the pane or area of the content panel that has the focus, as {@link RepoView#focusedPane}
 *                    keeps it; none for the files, where the app starts
 * @param then        the message to handle once the status, the branches and the worktrees of the repository are
 *                    read, as when a checkout asked for in another worktree is to run there (ADR 68)
 * @param tabs        the tab each pane with tabs shows, as {@link RepoView#tabs} keeps it (ADR 185)
 */
public record Landing(Option<Focus.OnScreen> focusedPane, Option<Msg> then, Map<PaneId, TabId> tabs) {

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
     * other pane with tabs shows its first.
     */
    public Landing(BranchesTab branchesTab, Option<Focus.OnScreen> focusedPane, Option<Msg> then) {
        this(focusedPane, then, HashMap.of(PaneId.BRANCHES, branchesTab.id()));
    }

    /**
     * Where {@code view} is: the same tabs, the list of the branches pane among them, the same pane focused.
     */
    public static Landing of(RepoView view) {
        return new Landing(view.focusedPane(), Option.none(), view.tabs());
    }

    /**
     * This landing, handling {@code msg} once the repository is read.
     */
    public Landing followedBy(Msg msg) {
        return new Landing(focusedPane, Option.of(msg), tabs);
    }

    /**
     * This landing, with each pane but the branches pane showing the tab it shows in {@code view}: another worktree of
     * the repository opens on the same tabs (ADR 185), but on the list of the branches pane this landing says.
     */
    public Landing keepingTabs(RepoView view) {
        return new Landing(
                focusedPane,
                then,
                tabs.get(PaneId.BRANCHES)
                        .fold(
                                () -> view.tabs().remove(PaneId.BRANCHES),
                                tab -> view.tabs().put(PaneId.BRANCHES, tab)));
    }

    /**
     * {@code view} shown here.
     */
    public RepoView applyTo(RepoView view) {
        return view.withFocusedPane(focusedPane).withTabs(tabs);
    }
}
