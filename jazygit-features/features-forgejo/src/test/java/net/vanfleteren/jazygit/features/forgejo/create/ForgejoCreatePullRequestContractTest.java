package net.vanfleteren.jazygit.features.forgejo.create;

import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.TIME;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.AfterPush;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestContract;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.FormOpening;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeCandidate;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;

/**
 * Creating a pull request on Forgejo does what it does alike on every forge (ADR 175): once where the branch is
 * pushed to is checked, its popup and its messages.
 */
class ForgejoCreatePullRequestContractTest extends CreatePullRequestContract {

    /**
     * The local {@code branches}, and the branches of origin, the home, they track.
     */
    @Override
    protected Model model(List<Branch> branches) {
        Model local =
                TestModels.localBranchesRead(TestModels.loaded(), branches).model();
        List<RemoteRef> remote = branches.flatMap(Branch::remoteUpstream)
                .map(branch -> new RemoteRef(branch, new Sha("e".repeat(40)), TIME));
        return TestModels.remoteBranchesRead(local, remote)
                .model()
                .withPart(
                        ForgeHome.PART,
                        ForgeHome.NONE.deciding(Option.of(new ForgeCandidate(
                                new FeatureId("forgejo"), "Forgejo", ORIGIN, PullRequestFixtures.URL))));
    }

    /**
     * As {@code o} leads to it, once where the branch is pushed to was checked.
     */
    @Override
    protected Next asked(Model model, BranchName branch) {
        return Update.update(model, new ForgejoCreatePullRequestMsg.HeadChecked(branch, Option.none()));
    }

    @Override
    protected AfterPush afterPush() {
        return new AskPullRequest();
    }

    @Override
    protected boolean asksWhatItSays(Next next, BranchName branch) {
        return next.cmds()
                .exists(cmd -> cmd instanceof ReadTitle title && title.branch().equals(branch));
    }

    @Override
    protected FormOpening opening(BranchName branch, List<BranchName> bases) {
        return new ForgejoNewPullRequestPopup.Opening(branch, bases);
    }

    @Override
    protected BranchRef baseRef(BranchName base) {
        return new BranchRef.Remote(new RemoteBranch(ORIGIN, base));
    }

    @Override
    protected Msg typed(Model model, String title) {
        return model.popup(ForgejoNewPullRequestPopup.class)
                .map(popup -> new ForgejoCreatePullRequestMsg.Edited(title, popup.body(), popup.draft()))
                .getOrElse(() -> new ForgejoCreatePullRequestMsg.Edited(title, "", false));
    }

    @Override
    protected Msg confirmed() {
        return new ForgejoCreatePullRequestMsg.Confirmed();
    }
}
