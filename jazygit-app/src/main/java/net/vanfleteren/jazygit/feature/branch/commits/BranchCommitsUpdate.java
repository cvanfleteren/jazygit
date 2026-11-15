package net.vanfleteren.jazygit.feature.branch.commits;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg.Closed;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg.Opened;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.BranchCommitsView;

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
