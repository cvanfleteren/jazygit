package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * Where the app shows a repository it opens in place of the one shown: the list of the branches pane, and the pane
 * that has the focus.
 *
 * @param branchesTab the list the branches pane shows
 * @param focusedPane the id of the pane that has the focus; none for the files, where the app starts
 */
public record Landing(BranchesTab branchesTab, Option<String> focusedPane) {

    /**
     * Where the app starts: the local branches, the files focused.
     */
    public static final Landing START = new Landing(BranchesTab.LOCAL, Option.none());

    /**
     * Where {@code view} is: the same list of the branches pane, the same pane focused.
     */
    public static Landing of(RepoView view) {
        return new Landing(view.branchesTab(), view.focusedPane());
    }

    /**
     * {@code view} shown here.
     */
    public RepoView applyTo(RepoView view) {
        return view.withBranchesTab(branchesTab).withFocusedPane(focusedPane);
    }
}
