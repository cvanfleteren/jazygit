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
 * @param branchAhead  the commits of the branch whose log is shown that are not on its base, keyed by the branch;
 *                     none in it when the branch has no base
 * @param commitDetail what the commit highlighted in the commits pane changed (its files, a summary and its
 *                     diff), once one is highlighted
 * @param fileDiff     the diff of the node highlighted in the files pane, keyed by the changed files under
 *                     it, once one is highlighted
 * @param stashDetail  what the stash highlighted in the stash pane stashed (its files, a summary and its diff),
 *                     keyed by its sha, which stays as stashes are added and dropped, once one is highlighted
 * @param focusedPane  the id of the element of the screen, outside the popups, that had the focus last, which a
 *                     closing popup gives it back to; none before the view reported one
 * @param cherryPick   the commit copied to be cherry-picked onto the branch checked out later, if one is
 * @param searchLine   the text of the line above the commits pane to type a search in, while it is open
 * @param commitSearch the search of the log of HEAD that was run last, which the commits pane lists in place of
 *                     the log until it is cleared
 * @param onlyAhead    whether the logs list only the commits not on their base, and the commit they forked from
 */
@With
public record RepoView(
        BranchesTab branchesTab,
        Option<DivergenceView> divergence,
        Option<Preview<BranchRef, List<Commit>>> branchLog,
        Option<Preview<BranchRef, Option<Ahead>>> branchAhead,
        Option<Preview<Sha, DiffText>> commitDetail,
        Option<Preview<List<FileEntry>, Diffs>> fileDiff,
        Option<Preview<Sha, DiffText>> stashDetail,
        Option<String> focusedPane,
        Option<Commit> cherryPick,
        Option<String> searchLine,
        Option<CommitSearch> commitSearch,
        boolean onlyAhead) {

    public static final RepoView INITIAL = new RepoView(
            BranchesTab.LOCAL,
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            false);
}
