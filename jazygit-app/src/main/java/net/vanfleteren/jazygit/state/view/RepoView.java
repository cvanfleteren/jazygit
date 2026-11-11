package net.vanfleteren.jazygit.state.view;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;

/**
 * What the repository screen shows, apart from what git reports: what is highlighted in its panes, the previews
 * read for that, and where its focus was. It stays while the pull requests screen is shown.
 *
 * @param branchesTab  which branches the branches pane lists; it stays when the focus leaves the pane
 * @param divergence   the commits a local branch and its upstream or base branch do not share, which the
 *                     branches pane lists in place of the local branches while it is open
 * @param branchCommits the commits of a branch, local or remote, which the branches pane lists in place of the
 *                      branches while it is open
 * @param branchLog    the log of the branch, local or remote, highlighted in the branches pane, once one is
 *                     highlighted
 * @param unfetched    the pull request of a stack highlighted in the branches pane in place of a branch, whose
 *                     branch is neither checked out nor fetched, so that no log shows (ADR 72)
 * @param branchAhead  the commits of the branch whose log is shown that are not on its base, keyed by the branch;
 *                     none in it when the branch has no base
 * @param commitDetail what the commit highlighted in the commits pane changed (its files, a summary and its
 *                     diff), once one is highlighted
 * @param fileDiff     the diff of the node highlighted in the files pane, keyed by the changed files under
 *                     it, once one is highlighted
 * @param stashDetail  what the stash highlighted in the stash pane stashed (its files, a summary and its diff),
 *                     keyed by its sha, which stays as stashes are added and dropped, once one is highlighted
 * @param commitFiles  the files a commit or a stash changed, which the files pane lists in place of the changed
 *                     files of the working tree while it is open
 * @param focusedPane  the id of the element of the screen, outside the popups, that had the focus last, which a
 *                     closing popup gives it back to; none before the view reported one
 * @param cherryPick   the commit copied to be cherry-picked onto the branch checked out later, if one is
 * @param searchLine   the text of the line above the commits pane to type a search in, while it is open
 * @param commitSearch the search of the log of HEAD that was run last, which the commits pane lists in place of
 *                     the log until it is cleared
 * @param filesFilter  the filter of the files of the working tree: the files pane shows only those that match it
 *                     (ADR 105)
 * @param onlyAhead    whether the logs list only the commits not on their base, and the commit they forked from
 * @param closedStacks the numbers of the stacks the branches pane shows closed, as only their header; a stack
 *                     starts open (ADR 72)
 * @param unstacked    whether the branches pane lists the branches of a stack as any other branch, by when they last
 *                     saw activity, with no header (ADR 87); it starts showing the stacks
 * @param expanded     the lines expanded around the hunks of the diff each area of the content panel shows, by the
 *                     id of the area (ADR 77)
 * @param versions     the version of its one file each area of the content panel shows in place of its diff, by
 *                     the id of the area, while another item was not highlighted (ADR 102)
 */
@With
public record RepoView(
        BranchesTab branchesTab,
        Option<DivergenceView> divergence,
        Option<BranchCommitsView> branchCommits,
        Option<Preview<BranchRef, List<Commit>>> branchLog,
        Option<Integer> unfetched,
        Option<Preview<BranchRef, Option<Ahead>>> branchAhead,
        Option<Preview<Sha, DiffText>> commitDetail,
        Option<Preview<List<FileEntry>, Diffs>> fileDiff,
        Option<Preview<Sha, DiffText>> stashDetail,
        Option<CommitFilesView> commitFiles,
        Option<String> focusedPane,
        Option<Commit> cherryPick,
        Option<String> searchLine,
        Option<CommitSearch> commitSearch,
        Option<FileFilter> filesFilter,
        boolean onlyAhead,
        Set<Integer> closedStacks,
        boolean unstacked,
        Map<AreaId, Expansion> expanded,
        Map<AreaId, Version> versions) {

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
            Option.none(),
            Option.none(),
            Option.none(),
            Option.none(),
            false,
            HashSet.empty(),
            false,
            HashMap.empty(),
            HashMap.empty());

    /**
     * The version kept for the area with the id {@code area} while it shows {@code content}, if it holds for that.
     */
    public Option<Version> version(AreaId area, ScrolledContent content) {
        return versions.get(area).filter(version -> version.holdsFor(content));
    }

    /**
     * The lines expanded in the area with the id {@code area} while it shows {@code content}, if they hold for that.
     */
    public Option<Expansion> expansion(AreaId area, ScrolledContent content) {
        return expanded.get(area).filter(kept -> kept.holdsFor(content));
    }
}
