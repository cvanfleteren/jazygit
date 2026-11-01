package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * Where the app shows a repository it opens in place of the one shown: the list of the branches pane, and the pane
 * that has the focus; and what it does there once it is read, if anything.
 *
 * @param branchesTab the list the branches pane shows
 * @param focusedPane the id of the pane that has the focus; none for the files, where the app starts
 * @param then        the message to handle once the branches and the worktrees of the repository are read, as when a
 *                    checkout asked for in another worktree is to run there (ADR 68)
 */
public record Landing(BranchesTab branchesTab, Option<String> focusedPane, Option<Msg> then) {

    /**
     * Where the app starts: the local branches, the files focused.
     */
    public static final Landing START = new Landing(BranchesTab.LOCAL, Option.none());

    /**
     * Showing the list {@code branchesTab}, with the pane {@code focusedPane} focused, and nothing more to do.
     */
    public Landing(BranchesTab branchesTab, Option<String> focusedPane) {
        this(branchesTab, focusedPane, Option.none());
    }

    /**
     * Where {@code view} is: the same list of the branches pane, the same pane focused.
     */
    public static Landing of(RepoView view) {
        return new Landing(view.branchesTab(), view.focusedPane());
    }

    /**
     * This landing, handling {@code msg} once the repository is read.
     */
    public Landing followedBy(Msg msg) {
        return new Landing(branchesTab, focusedPane, Option.of(msg));
    }

    /**
     * {@code view} shown here.
     */
    public RepoView applyTo(RepoView view) {
        return view.withBranchesTab(branchesTab).withFocusedPane(focusedPane);
    }
}
