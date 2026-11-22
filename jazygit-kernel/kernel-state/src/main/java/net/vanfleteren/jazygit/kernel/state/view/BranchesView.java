package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;

/**
 * What the branches pane shows, apart from the previews read for the branch highlighted in it.
 *
 * @param tab           which branches the pane lists; it stays when the focus leaves the pane
 * @param divergence    the commits a local branch and its upstream or base branch do not share, which the pane lists
 *                      in place of the local branches while it is open
 * @param branchCommits the commits of a branch, local or remote, which the pane lists in place of the branches while
 *                      it is open
 * @param noLog         the row highlighted in the pane in place of a branch that has no log, and why: what belongs to
 *                      a group of branches but is neither checked out nor fetched (ADR 72), or a detached HEAD
 * @param closedGroups  the keys of the groups of branches the pane shows closed, as only their header; a group starts
 *                      open (ADR 72, ADR 167)
 * @param grouped       whether the pane lists the branches of a group under its header, or as any other branch
 *                      (ADR 72)
 */
@With
public record BranchesView(
        BranchesTab tab,
        Option<DivergenceView> divergence,
        Option<BranchCommitsView> branchCommits,
        Option<NoLog> noLog,
        Set<String> closedGroups,
        boolean grouped) {

    public static final BranchesView INITIAL =
            new BranchesView(BranchesTab.LOCAL, Option.none(), Option.none(), Option.none(), HashSet.empty(), true);
}
