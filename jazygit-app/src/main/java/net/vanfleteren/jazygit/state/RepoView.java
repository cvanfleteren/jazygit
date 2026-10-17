package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.*;

/**
 * What the repository screen shows, apart from what git reports: what is highlighted in its panes, the previews
 * read for that, and where its focus was. It stays while the pull requests screen is shown.
 *
 * @param branchesTab  which branches the branches pane lists; it stays when the focus leaves the pane
 * @param divergence   the commits a local branch and its upstream or base branch do not share, which the
 *                     branches pane lists in place of the local branches while it is open
 * @param branchLog    the log of the branch, local or remote, highlighted in the branches pane, once one is
 *                     highlighted
 * @param commitDetail what the commit highlighted in the commits pane changed (its files, a summary and its
 *                     diff), once one is highlighted
 * @param fileDiff     the diff of the node highlighted in the files pane, keyed by the changed files under
 *                     it, once one is highlighted
 * @param focusedPane  the id of the element of the screen, outside the popups, that had the focus last, which a
 *                     closing popup gives it back to; none before the view reported one
 * @param cherryPick   the commit copied to be cherry-picked onto the branch checked out later, if one is
 */
@With
public record RepoView(
        BranchesTab branchesTab,
        Option<DivergenceView> divergence,
        Option<Preview<BranchRef, List<Commit>>> branchLog,
        Option<Preview<Sha, DiffText>> commitDetail,
        Option<Preview<List<FileEntry>, Diffs>> fileDiff,
        Option<String> focusedPane,
        Option<Commit> cherryPick) {

    public static final RepoView INITIAL = new RepoView(
            BranchesTab.LOCAL,
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none());
}
