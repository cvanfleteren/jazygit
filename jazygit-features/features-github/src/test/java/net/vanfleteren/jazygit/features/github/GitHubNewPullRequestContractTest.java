package net.vanfleteren.jazygit.features.github;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.AfterPush;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.FormOpening;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestContract;
import net.vanfleteren.jazygit.features.github.create.AskPullRequest;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.ReadParent;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestMsg;
import net.vanfleteren.jazygit.features.github.create.NewPullRequestPopup;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Creating a pull request on GitHub does what it does alike on every forge (ADR 175): {@code o} on a branch without
 * one, its popup and its messages.
 */
class GitHubNewPullRequestContractTest extends NewPullRequestContract {

    @Override
    protected Model model(List<Branch> branches) {
        Model read = TestModels.localBranchesRead(PullRequestFixtures.loadedWithTheFeature(), branches)
                .model();
        return Update.update(read, PullRequestFixtures.listed(List.empty())).model();
    }

    @Override
    protected Next asked(Model model, BranchName branch) {
        return Update.update(model, new PullRequestMsg.Requested(branch));
    }

    @Override
    protected AfterPush afterPush() {
        return new AskPullRequest();
    }

    /**
     * With several bases, GitHub reads which the branch came from before its title.
     */
    @Override
    protected boolean asksWhatItSays(Next next, BranchName branch) {
        return next.cmds()
                .exists(cmd -> cmd instanceof ReadParent parent
                                && parent.branch().equals(branch)
                        || cmd instanceof ReadTitle title && title.branch().equals(branch));
    }

    @Override
    protected FormOpening opening(BranchName branch, List<BranchName> bases) {
        return new NewPullRequestPopup.Opening(branch, new RemoteBranch("origin", branch), bases);
    }

    @Override
    protected BranchRef baseRef(BranchName base) {
        return new BranchRef.Remote(new RemoteBranch("origin", base));
    }

    @Override
    protected Msg typed(Model model, String title) {
        NewPullRequest typed = model.popup(NewPullRequestPopup.class)
                .map(NewPullRequestPopup::pullRequest)
                .getOrElse(new NewPullRequest("", "", false));
        return new CreatePullRequestMsg.PullRequestEdited(new NewPullRequest(title, typed.body(), typed.draft()));
    }

    @Override
    protected Msg confirmed() {
        return new CreatePullRequestMsg.Confirmed();
    }
}
