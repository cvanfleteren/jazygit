package net.vanfleteren.jazygit.github;

import static net.vanfleteren.jazygit.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.branches.branch.BranchError;
import net.vanfleteren.jazygit.branches.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.branches.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.branches.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.github.create.CreatePullRequestCmd;
import net.vanfleteren.jazygit.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.github.create.CreatePullRequestMsg;
import net.vanfleteren.jazygit.github.create.NewPullRequestPopup;
import net.vanfleteren.jazygit.github.create.PushFirstPopup;
import net.vanfleteren.jazygit.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.operation.*;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.state.popup.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Pushing a branch that is not on its remote, or ahead of it, before its pull request is created.
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
                        new PullRequestsMsg.Loaded(List.empty()))
                .model();

        Next reading = Update.update(model, new CreatePullRequestMsg.ParentRead(login.name(), Option.none()));
        ReadTitle readTitle = (ReadTitle) reading.cmds().single();
        assertEquals(pushedAs, readTitle.head());
        Model asked = Update.update(reading.model(), readTitle.run(env())).model();
        Next confirmed = TestModels.stepped(asked, new CreatePullRequestMsg.Confirmed());
        CreatePullRequest create = (CreatePullRequest) confirmed.cmds().single();

        assertEquals(pushedAs, create.head());
        CreatePullRequestMsg.Created created = (CreatePullRequestMsg.Created) create.run(env());
        assertTrue(
                created.commands().exists(command -> command.line().contains("--head feature/login --base main")),
                created.commands().toString());
    }

    @Test
    void aBranchNotOnItsRemoteIsAskedToBePushedToItsPushTargetFirst() {
        Next asking = Update.update(model(), new PullRequestMsg.Requested(UNPUSHED.name()));

        assertEquals(
                Option.of(new PushFirstPopup(
                        UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name()), Option.none())),
                asking.model().popup());
        assertEquals(List.empty(), asking.cmds());
        assertEquals(Option.none(), asking.model().outcome().error());
    }

    @Test
    void aBranchAheadOfItsRemoteIsAskedToPushItsCommitsFirst() {
        Next asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()));

        assertEquals(
                Option.of(new PushFirstPopup(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()), Option.of(2))),
                asking.model().popup());
        assertEquals(List.empty(), asking.cmds());
    }

    @Test
    void aDivergedBranchIsNotForcePushedForItsPullRequest() {
        RemoteBranch upstream = new RemoteBranch("origin", new BranchName("diverged"));
        Branch diverged = Fixtures.branch(
                upstream.branch(),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(upstream), 2, 1)));
        Model branches = localBranchesRead(loaded(), List.of(diverged, MAIN)).model();
        Model starting = Update.update(branches, new PullRequestsMsg.Loaded(List.empty()))
                .model();

        Next refused = Update.update(starting, new PullRequestMsg.Requested(diverged.name()));

        assertEquals(
                Option.of(new BranchError.Diverged(diverged.name(), upstream)),
                refused.model().outcome().error());
        assertTrue(refused.model().popup(PushFirstPopup.class).isEmpty());
        assertEquals(List.empty(), refused.cmds());
    }

    @Test
    void confirmingPushesABranchNotOnItsRemoteToItsPushTargetWhichItThenTracks() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(UNPUSHED.name()))
                .model();

        Next pushing = TestModels.stepped(asking, new CreatePullRequestMsg.PushFirstConfirmed());

        assertEquals(
                List.of(new PushFirst(
                        new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())))),
                pushing.cmds());
        assertEquals(Option.none(), pushing.model().popup());
        assertTrue(pushing.model().idleBranch(UNPUSHED.name()).isEmpty());
    }

    @Test
    void confirmingPushesTheCommitsOfABranchAheadOfItsRemote() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()))
                .model();

        Next pushing = Update.update(asking, new CreatePullRequestMsg.PushFirstConfirmed());

        assertEquals(
                List.of(new PushFirst(
                        new Push(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()), PushMode.FAST_FORWARD))),
                pushing.cmds());
    }

    @Test
    void cancellingThePushPushesNothing() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(UNPUSHED.name()))
                .model();

        Next cancelled = Update.update(asking, new PopupMsg.Dismissed(PushFirstPopup.class));

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void onceItIsPushedItsPullRequestIsAskedForWhileTheRepositoryIsReadAgain() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
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
    void aFailedPushAsksForNoPullRequest() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model pushing = OperationUpdate.start(model(), push).model();

        Next failed = Update.update(pushing, failed(push, "rejected"));

        assertFalse(
                failed.cmds().exists(CreatePullRequestCmd.class::isInstance),
                failed.cmds().toString());
        assertTrue(failed.model().outcome().error().isDefined());
        assertTrue(failed.model().popup(NewPullRequestPopup.class).isEmpty());
    }

    /**
     * {@link #AHEAD}, but {@code ahead} and {@code behind} its upstream.
     */
    private static Branch ahead(int ahead, int behind) {
        return Fixtures.branch(
                AHEAD.name(),
                false,
                new Sha("5"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", AHEAD.name())), ahead, behind)));
    }

    @Test
    void confirmingOnceItWasPushedMeanwhileAsksForItsPullRequest() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()))
                .model();
        Model pushedElsewhere = localBranchesRead(asking, List.of(RELEASE, PUSHED, UNPUSHED, ahead(0, 0), MAIN))
                .model();

        Next confirmed = Update.update(pushedElsewhere, new CreatePullRequestMsg.PushFirstConfirmed());

        assertTrue(confirmed.model().popup(PushFirstPopup.class).isEmpty());
        assertFalse(
                confirmed.cmds().exists(PushFirst.class::isInstance),
                confirmed.cmds().toString());
        assertTrue(
                confirmed.cmds().exists(ReadParent.class::isInstance),
                confirmed.cmds().toString());
    }

    @Test
    void confirmingOnceItDivergedMeanwhileSaysSo() {
        Model asking = Update.update(model(), new PullRequestMsg.Requested(AHEAD.name()))
                .model();
        Model diverged = localBranchesRead(asking, List.of(RELEASE, PUSHED, UNPUSHED, ahead(2, 1), MAIN))
                .model();

        Next confirmed = Update.update(diverged, new CreatePullRequestMsg.PushFirstConfirmed());

        assertTrue(confirmed.model().popup(PushFirstPopup.class).isEmpty());
        assertEquals(
                Option.of(new BranchError.Diverged(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()))),
                confirmed.model().outcome().error());
        assertEquals(List.empty(), confirmed.cmds());
    }

    @Test
    void aPushedBranchThatIsGoneMeanwhileAsksForNoPullRequest() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model pushing = OperationUpdate.start(model(), push).model();
        Model gone = localBranchesRead(pushing, List.of(RELEASE, PUSHED, AHEAD, MAIN))
                .model();
        List<ExecutedCommand> commands = StubGit.executed("git push -u origin local");

        Next pushed = Update.update(gone, push.succeeded(commands));

        assertEquals(OperationUpdate.succeeded(gone, push, commands).cmds(), pushed.cmds());
        assertTrue(pushed.model().popup(NewPullRequestPopup.class).isEmpty());
    }

    @Test
    void aPushThatEndsWhileAnotherPopupIsOpenLeavesThatOpen() {
        PushFirst push =
                new PushFirst(new PushSettingUpstream(UNPUSHED.name(), new RemoteBranch("origin", UNPUSHED.name())));
        Model other = OperationUpdate.start(model(), push)
                .model()
                .openPopup(new SwitchWorktreePopup(MAIN.name(), "/work/main"));
        Model pushed = Update.update(other, push.succeeded(StubGit.executed("git push -u origin local")))
                .model();

        Model parentRead = Update.update(pushed, new CreatePullRequestMsg.ParentRead(UNPUSHED.name(), Option.none()))
                .model();
        Model titleRead = Update.update(
                        parentRead,
                        new CreatePullRequestMsg.TitleRead(
                                UNPUSHED.name(),
                                new RemoteBranch("origin", UNPUSHED.name()),
                                List.of(MAIN.name()),
                                Option.of("Add it")))
                .model();

        assertEquals(other.popup(), titleRead.popup());
    }
}
