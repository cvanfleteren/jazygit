package net.vanfleteren.jazygit.features.branches.branch.commits;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg.Closed;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg.Opened;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.BranchCommitsView;

/**
 * Listing the commits of a branch in place of the branches, and going back to them. The commits are the log of the
 * highlighted branch, which the branch opened is: it is read already, unless it was only just highlighted.
 */
public final class BranchCommitsUpdate {

    private BranchCommitsUpdate() {}

    public static Next update(Model model, BranchCommitsMsg msg) {
        return switch (msg) {
            case Opened(var branch) ->
                new SelectionMsg.BranchSelected(branch)
                        .apply(model.mapRepoView(repoView -> repoView.mapBranches(
                                        branches -> branches.withBranchCommits(Option.of(BranchCommitsView.of(branch))))
                                .mapBranches(branches -> branches.withDivergence(Option.none()))));
            case Closed() ->
                Next.of(model.mapRepoView(
                        repoView -> repoView.mapBranches(branches -> branches.withBranchCommits(Option.none()))));
        };
    }
}
