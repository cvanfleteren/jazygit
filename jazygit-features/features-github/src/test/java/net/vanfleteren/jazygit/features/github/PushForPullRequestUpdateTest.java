package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.branches.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.ReadTitle;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestMsg;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.features.github.create.AskPullRequest;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestMsg;
import net.vanfleteren.jazygit.features.github.create.GitHubNewPullRequestPopup;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Pushing a branch before its pull request is created, as far as GitHub's way on from there goes: what pushing it
 * first does on every forge is {@code GitHubCreatePullRequestContractTest}'s.
 */
class PushForPullRequestUpdateTest {

    @Test
    void aPullRequestIsCreatedFromTheNameTheBranchHasOnItsRemoteNotItsLocalOne() {
        // #138: local login is pushed to origin/feature/login; gh has to be given that name.
        RemoteBranch pushedAs = new RemoteBranch("origin", new BranchName("feature/login"));
        Branch login = Fixtures.branch(
                new BranchName("login"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(pushedAs), 0, 0)));
        Model model = Update.update(
                        localBranchesRead(loaded(), List.of(login, MAIN)).model(),
                        PullRequestFixtures.listed(List.empty()))
                .model();

        Next reading = Update.update(model, new GitHubCreatePullRequestMsg.ParentRead(login.name(), Option.none()));
        ReadTitle readTitle = (ReadTitle) reading.cmds().single();
        assertEquals(pushedAs, ((GitHubNewPullRequestPopup.Opening) readTitle.opening()).head());
        Model asked = Update.update(reading.model(), readTitle.run(env())).model();
        Next confirmed = TestModels.stepped(asked, new GitHubCreatePullRequestMsg.Confirmed());
        CreatePullRequest create = (CreatePullRequest) confirmed.cmds().single();

        assertEquals(pushedAs, create.head());
        GitHubCreatePullRequestMsg.Created created = (GitHubCreatePullRequestMsg.Created) create.run(env());
        assertTrue(
                created.commands().exists(command -> command.line().contains("--head feature/login --base main")),
                created.commands().toString());
    }

    @Test
    void onceItIsPushedItsPullRequestIsAskedForWhileTheRepositoryIsReadAgain() {
        PushFirst push = new PushFirst(
                new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())),
                new AskPullRequest());
        Model pushing = OperationUpdate.start(model(), push).model();

        List<ExecutedCommand> commands = StubGit.executed("git push -u origin local");

        Next pushed = Update.update(pushing, push.succeeded(commands));

        assertEquals(
                List.of(new ReadParent(
                        UNPUSHED.name(),
                        List.of(
                                new Base(MAIN.name(), remote("main")),
                                new Base(RELEASE.name(), remote("release")),
                                new Base(FEATURE, remote("feature")),
                                new Base(AHEAD.name(), remote("ahead"))))),
                pushed.cmds().filter(ReadParent.class::isInstance));
        // What any push does once it succeeded is done as well.
        assertTrue(
                pushed.cmds()
                        .containsAll(OperationUpdate.succeeded(pushing, push, commands)
                                .cmds()),
                pushed.cmds().toString());
        assertTrue(pushed.model().idleBranch(UNPUSHED.name()).isDefined());
    }

    @Test
    void aPushThatEndsWhileAnotherPopupIsOpenLeavesThatOpen() {
        PushFirst push = new PushFirst(
                new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())),
                new AskPullRequest());
        Model other = OperationUpdate.start(model(), push)
                .model()
                .openPopup(new SwitchWorktreePopup(MAIN.name(), "/work/main"));
        Model pushed = Update.update(other, push.succeeded(StubGit.executed("git push -u origin local")))
                .model();

        Model parentRead = Update.update(
                        pushed, new GitHubCreatePullRequestMsg.ParentRead(UNPUSHED.name(), Option.none()))
                .model();
        Model titleRead = Update.update(
                        parentRead,
                        new CreatePullRequestMsg.TitleRead(
                                UNPUSHED.name(),
                                new GitHubNewPullRequestPopup.Opening(
                                        UNPUSHED.name(),
                                        new RemoteBranch("origin", UNPUSHED.name()),
                                        List.of(MAIN.name())),
                                Option.of("Add it")))
                .model();

        assertEquals(other.popup(), titleRead.popup());
    }
}
