package net.vanfleteren.jazygit.state.view;

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
 * @param noLog         the row highlighted in the pane in place of a branch that has no log, and why: a pull request
 *                      of a stack whose branch is neither checked out nor fetched (ADR 72), or a detached HEAD
 * @param closedStacks  the numbers of the stacks the pane shows closed, as only their header; a stack starts open
 *                      (ADR 72)
 * @param stacking      whether the pane lists the branches of a stack under a header, or as any other branch (ADR 87)
 */
@With
public record BranchesView(
        BranchesTab tab,
        Option<DivergenceView> divergence,
        Option<BranchCommitsView> branchCommits,
        Option<NoLog> noLog,
        Set<Integer> closedStacks,
        Stacking stacking) {

    public static final BranchesView INITIAL = new BranchesView(
            BranchesTab.LOCAL, Option.none(), Option.none(), Option.none(), HashSet.empty(), Stacking.STACKED);
}
