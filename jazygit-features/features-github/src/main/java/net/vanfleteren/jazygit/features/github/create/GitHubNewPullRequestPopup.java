package net.vanfleteren.jazygit.features.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.FormOpening;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestForm;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * Asking what the new pull request of {@code branch} says: which of {@code bases} it merges into, its title, its
 * body, and whether it is a draft.
 *
 * @param head        the branch on its remote that it pushes to, which gh needs
 * @param bases       the branches to choose from, the parent first
 * @param base        the one picked: the highlighted one, or the one highlighted last while the filter shows none
 * @param titleRead   the title read last, for a base: the subject of the oldest commit not on it
 * @param pullRequest what it says as far as the user typed and ticked it; the title starts out as {@code titleRead}
 * @param stack       whether it is stacked on the pull request of its base, when that has one (ADR 73)
 */
@With
public record GitHubNewPullRequestPopup(
        BranchName branch,
        RemoteBranch head,
        List<BranchName> bases,
        BranchName base,
        String titleRead,
        NewPullRequest pullRequest,
        boolean stack)
        implements NewPullRequestForm {

    @Override
    public String title() {
        return pullRequest.title();
    }

    @Override
    public GitHubNewPullRequestPopup titled(String title) {
        return withTitleRead(title).withPullRequest(new NewPullRequest(title, pullRequest.body(), pullRequest.draft()));
    }

    /**
     * The branch {@code base} is on its remote, which a pull request merges into; the local one when it has no
     * upstream there.
     */
    @Override
    public Option<BranchRef> baseRef(Model model, BranchName base) {
        return Option.of(GitHubCreatePullRequestUpdate.baseRef(model, base));
    }

    /**
     * Opening the popup for {@code branch}, which pushes to {@code head}, into the first of {@code bases}, once its
     * title is read: stacked on the pull request of its base, where it has one.
     */
    public record Opening(BranchName branch, RemoteBranch head, List<BranchName> bases) implements FormOpening {

        @Override
        public GitHubNewPullRequestPopup titled(String title) {
            return new GitHubNewPullRequestPopup(
                    branch, head, bases, bases.head(), title, new NewPullRequest(title, "", false), true);
        }
    }
}
