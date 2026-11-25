package net.vanfleteren.jazygit.features.github.create;

import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.AfterPush;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Once a branch is on its remote with all its commits, asking what its pull request on GitHub merges into and what it
 * says, as {@link CreatePullRequestUpdate#ask} does.
 */
public record AskPullRequest() implements AfterPush {

    @Override
    public Next next(Model model, Branch branch) {
        return CreatePullRequestUpdate.ask(model, branch);
    }
}
