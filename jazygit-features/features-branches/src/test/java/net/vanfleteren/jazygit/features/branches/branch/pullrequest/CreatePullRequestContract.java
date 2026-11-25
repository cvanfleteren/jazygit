package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.PushMode;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.branches.branch.checkout.SwitchWorktreePopup;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.CreatePullRequestCmd.RereadTitle;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * What creating a pull request does alike on every forge (ADR 175): pushing the branch first (ADR 25), and the title
 * that follows the base picked until the user types one (ADR 18). Each forge runs it on its own model, messages and
 * popup by extending it; what a forge does on its own, as which bases it offers and what it sends, its own tests
 * check.
 */
public abstract class CreatePullRequestContract {

    /**
     * The default branch, on origin.
     */
    protected static final Branch MAIN = tracking("main", true, 0, 0);

    /**
     * Another branch on origin.
     */
    protected static final Branch RELEASE = tracking("release", false, 0, 0);

    /**
     * The branch a pull request is created for, on origin with all its commits.
     */
    protected static final Branch FEATURE = tracking("feature", false, 0, 0);

    /**
     * A branch that tracks no remote branch.
     */
    protected static final Branch LOCAL = new Branch(
            new BranchName("local"),
            false,
            new Sha("1".repeat(40)),
            Instant.EPOCH,
            Option.none(),
            false,
            false,
            new Tracking.None(),
            Option.none());

    /**
     * A branch with two commits its remote branch does not have.
     */
    protected static final Branch AHEAD = tracking("ahead", false, 2, 0);

    /**
     * The branches of {@link #standard()}: the default branch last, so that a forge offering it first does so itself.
     */
    protected static final List<Branch> BRANCHES = List.of(RELEASE, FEATURE, LOCAL, AHEAD, MAIN);

    /**
     * A branch named {@code name} that tracks {@code origin/<name>}, {@code ahead} and {@code behind} it.
     */
    protected static Branch tracking(String name, boolean isDefault, int ahead, int behind) {
        return new Branch(
                new BranchName(name),
                false,
                new Sha("f".repeat(40)),
                Instant.EPOCH,
                Option.none(),
                true,
                isDefault,
                new Tracking.Tracks(new Upstream(
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName(name))), ahead, behind)),
                Option.none());
    }

    /**
     * A model where the forge applies, with {@code branches} read and the remote branches they track on origin, and
     * where none of them has a pull request, as far as the forge knows.
     */
    protected abstract Model model(List<Branch> branches);

    /**
     * {@code model}, where the user asked for the pull request of {@code branch}, which has none: as far as the forge
     * gets before the branch is pushed, or the title is read.
     */
    protected abstract Next asked(Model model, BranchName branch);

    /**
     * What the forge does once a branch is pushed.
     */
    protected abstract AfterPush afterPush();

    /**
     * Whether {@code next} goes on to ask what the pull request of {@code branch} says: it reads what the popup opens
     * with.
     */
    protected abstract boolean asksWhatItSays(Next next, BranchName branch);

    /**
     * How the forge opens its popup for {@code branch} into the first of {@code bases}.
     */
    protected abstract FormOpening opening(BranchName branch, List<BranchName> bases);

    /**
     * The branch {@code base} is on its remote, which the forge reads the title against.
     */
    protected abstract BranchRef baseRef(BranchName base);

    /**
     * The forge's message of the user typing {@code title} in its popup, leaving the rest as it is.
     */
    protected abstract Msg typed(Model model, String title);

    /**
     * The forge's message of the user confirming its popup.
     */
    protected abstract Msg confirmed();

    private Model standard() {
        return model(BRANCHES);
    }

    /**
     * {@link #standard()} with the popup open for {@link #FEATURE}, its title read as {@code Add login} for main, offered
     * before release.
     */
    private Model creating() {
        return Update.update(standard(), titleRead(Option.of("Add login"))).model();
    }

    private Msg titleRead(Option<String> subject) {
        return new CreatePullRequestMsg.TitleRead(
                FEATURE.name(), opening(FEATURE.name(), List.of(MAIN.name(), RELEASE.name())), subject);
    }

    private static NewPullRequestForm form(Model model) {
        return model.popup(NewPullRequestForm.class).get();
    }

    private static Msg moved(int at, BranchName base) {
        return new CreatePullRequestMsg.BaseMoved(ListCursor.START.at(at), Option.of(base));
    }

    private static Msg baseTitleRead(BranchName base, String subject) {
        return new CreatePullRequestMsg.BaseTitleRead(FEATURE.name(), base, Option.of(subject));
    }

    private PushFirst pushFirst(Branch branch) {
        return new PushFirst(new PushSettingUpstream(branch.name(), branch.pushTarget()), afterPush());
    }

    // Pushing first (ADR 25).

    @Test
    void aBranchOnItsRemoteWithAllItsCommitsIsAskedWhatItsPullRequestSays() {
        Next next = asked(standard(), FEATURE.name());

        assertTrue(next.model().popup(PushFirstPopup.class).isEmpty());
        assertTrue(asksWhatItSays(next, FEATURE.name()), next.cmds().toString());
    }

    @Test
    void aBranchWithoutAnUpstreamIsAskedToBePushedToItsPushTargetFirst() {
        Next next = asked(standard(), LOCAL.name());

        assertEquals(
                Option.of(new PushFirstPopup(
                        LOCAL.name(), new RemoteBranch("origin", LOCAL.name()), Option.none(), afterPush())),
                next.model().popup(PushFirstPopup.class));
        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.none(), next.model().outcome().error());
    }

    @Test
    void aBranchAheadOfItsUpstreamIsAskedToPushItsCommitsFirst() {
        Next next = asked(standard(), AHEAD.name());

        assertEquals(
                Option.of(new PushFirstPopup(
                        AHEAD.name(), new RemoteBranch("origin", AHEAD.name()), Option.of(2), afterPush())),
                next.model().popup(PushFirstPopup.class));
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void aDivergedBranchIsNotForcePushedForItsPullRequest() {
        Model diverged = model(List.of(RELEASE, FEATURE, tracking("ahead", false, 2, 1), MAIN));

        Next next = asked(diverged, AHEAD.name());

        assertEquals(
                Option.of(new BranchError.Diverged(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()))),
                next.model().outcome().error());
        assertTrue(next.model().popup(PushFirstPopup.class).isEmpty());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void confirmingPushesABranchWithoutAnUpstreamToItsPushTargetWhichItThenTracks() {
        Model asking = asked(standard(), LOCAL.name()).model();

        Next pushing = TestModels.stepped(asking, new CreatePullRequestMsg.PushFirstConfirmed());

        assertEquals(List.of(pushFirst(LOCAL)), pushing.cmds());
        assertEquals(Option.none(), pushing.model().popup());
        assertTrue(pushing.model().idleBranch(LOCAL.name()).isEmpty());
    }

    @Test
    void confirmingPushesTheCommitsOfABranchAheadOfItsUpstream() {
        Model asking = asked(standard(), AHEAD.name()).model();

        Next pushing = Update.update(asking, new CreatePullRequestMsg.PushFirstConfirmed());

        assertEquals(
                List.of(new PushFirst(
                        new Push(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()), PushMode.FAST_FORWARD),
                        afterPush())),
                pushing.cmds());
    }

    @Test
    void cancellingThePushPushesNothing() {
        Model asking = asked(standard(), LOCAL.name()).model();

        Next cancelled = Update.update(asking, new PopupMsg.Dismissed(PushFirstPopup.class));

        assertEquals(Option.none(), cancelled.model().popup());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void confirmingOnceItWasPushedMeanwhileAsksWhatItsPullRequestSays() {
        Model asking = asked(standard(), AHEAD.name()).model();
        Model pushedElsewhere = TestModels.localBranchesRead(
                        asking, List.of(RELEASE, FEATURE, LOCAL, tracking("ahead", false, 0, 0), MAIN))
                .model();

        Next confirmed = Update.update(pushedElsewhere, new CreatePullRequestMsg.PushFirstConfirmed());

        assertTrue(confirmed.model().popup(PushFirstPopup.class).isEmpty());
        assertFalse(
                confirmed.cmds().exists(PushFirst.class::isInstance),
                confirmed.cmds().toString());
        assertTrue(asksWhatItSays(confirmed, AHEAD.name()), confirmed.cmds().toString());
    }

    @Test
    void confirmingOnceItDivergedMeanwhileSaysSo() {
        Model asking = asked(standard(), AHEAD.name()).model();
        Model diverged = TestModels.localBranchesRead(
                        asking, List.of(RELEASE, FEATURE, LOCAL, tracking("ahead", false, 2, 1), MAIN))
                .model();

        Next confirmed = Update.update(diverged, new CreatePullRequestMsg.PushFirstConfirmed());

        assertTrue(confirmed.model().popup(PushFirstPopup.class).isEmpty());
        assertEquals(
                Option.of(new BranchError.Diverged(AHEAD.name(), new RemoteBranch("origin", AHEAD.name()))),
                confirmed.model().outcome().error());
        assertEquals(List.empty(), confirmed.cmds());
    }

    @Test
    void onceItIsPushedWhatItsPullRequestSaysIsAskedAndWhatAnyPushDoesIsDone() {
        PushFirst push = pushFirst(LOCAL);
        Model pushing = OperationUpdate.start(standard(), push).model();
        List<ExecutedCommand> commands = StubGit.executed("git push -u origin local");

        Next pushed = Update.update(pushing, push.succeeded(commands));

        assertTrue(asksWhatItSays(pushed, LOCAL.name()), pushed.cmds().toString());
        assertTrue(
                pushed.cmds()
                        .containsAll(OperationUpdate.succeeded(pushing, push, commands)
                                .cmds()),
                pushed.cmds().toString());
        assertTrue(pushed.model().idleBranch(LOCAL.name()).isDefined());
    }

    @Test
    void aPushedBranchThatIsGoneMeanwhileAsksNothing() {
        PushFirst push = pushFirst(LOCAL);
        Model pushing = OperationUpdate.start(standard(), push).model();
        Model gone = TestModels.localBranchesRead(pushing, List.of(RELEASE, FEATURE, AHEAD, MAIN))
                .model();
        List<ExecutedCommand> commands = StubGit.executed("git push -u origin local");

        Next pushed = Update.update(gone, push.succeeded(commands));

        assertEquals(OperationUpdate.succeeded(gone, push, commands).cmds(), pushed.cmds());
        assertTrue(pushed.model().popup(NewPullRequestForm.class).isEmpty());
    }

    @Test
    void aFailedPushAsksNothing() {
        PushFirst push = pushFirst(LOCAL);
        Model pushing = OperationUpdate.start(standard(), push).model();

        Next failed = Update.update(pushing, TestModels.failed(push, "rejected"));

        assertFalse(asksWhatItSays(failed, LOCAL.name()), failed.cmds().toString());
        assertTrue(failed.model().outcome().error().isDefined());
        assertTrue(failed.model().popup(NewPullRequestForm.class).isEmpty());
    }

    // The title that follows the base (ADR 18).

    @Test
    void thePopupOpensWithTheTitleReadForTheBaseOfferedFirst() {
        NewPullRequestForm form = form(creating());

        assertEquals(FEATURE.name(), form.branch());
        assertEquals(MAIN.name(), form.base());
        assertEquals("Add login", form.title());
        assertTrue(form.titleUntouched());
    }

    @Test
    void withoutACommitOfItsOwnTheTitleIsTheBranch() {
        Model asking = Update.update(standard(), titleRead(Option.none())).model();

        assertEquals("feature", form(asking).title());
    }

    @Test
    void theTitleReadLeavesAnotherPopupOpen() {
        Model busy = standard().openPopup(new SwitchWorktreePopup(MAIN.name(), "/work/main"));

        assertEquals(
                busy.popup(),
                Update.update(busy, titleRead(Option.of("Add login"))).model().popup());
    }

    @Test
    void anotherBaseIsPickedAndTheTitleReadForIt() {
        Next moved = Update.update(creating(), moved(1, RELEASE.name()));
        Model read = Update.update(moved.model(), baseTitleRead(RELEASE.name(), "Fix release"))
                .model();

        assertEquals(RELEASE.name(), form(moved.model()).base());
        assertEquals(ListCursor.START.at(1), moved.model().listCursor());
        assertEquals(List.of(new RereadTitle(FEATURE.name(), RELEASE.name(), baseRef(RELEASE.name()))), moved.cmds());
        assertEquals("Fix release", form(read).title());
        assertEquals(ListCursor.START.at(1), read.listCursor());
    }

    @Test
    void aTitleTypedIsNotReadAgain() {
        Model typed = Update.update(creating(), typed(creating(), "Mine")).model();

        Next moved = Update.update(typed, moved(1, RELEASE.name()));
        Model read = Update.update(moved.model(), baseTitleRead(RELEASE.name(), "Fix release"))
                .model();

        assertEquals(RELEASE.name(), form(moved.model()).base());
        assertEquals(List.empty(), moved.cmds());
        assertEquals("Mine", form(read).title());
    }

    @Test
    void aTitleTypedBackToTheOneReadFollowsTheBaseAgain() {
        Model mine = Update.update(creating(), typed(creating(), "Mine")).model();
        Model back = Update.update(mine, typed(mine, "Add login")).model();

        Next moved = Update.update(back, moved(1, RELEASE.name()));
        Model read = Update.update(moved.model(), baseTitleRead(RELEASE.name(), "Fix release"))
                .model();

        assertEquals(List.of(new RereadTitle(FEATURE.name(), RELEASE.name(), baseRef(RELEASE.name()))), moved.cmds());
        assertEquals("Fix release", form(read).title());
    }

    @Test
    void aTitleReadForABaseNoLongerPickedIsDropped() {
        Model release = Update.update(creating(), moved(1, RELEASE.name())).model();
        Model main = Update.update(release, moved(0, MAIN.name())).model();

        assertEquals(
                main,
                Update.update(main, baseTitleRead(RELEASE.name(), "Fix release"))
                        .model());
    }

    @Test
    void aFilterThatShowsNoBaseKeepsTheBase() {
        ListCursor cursor = new ListCursor(Option.of(0), Option.of("nothing"));

        Next moved = Update.update(creating(), new CreatePullRequestMsg.BaseMoved(cursor, Option.none()));

        assertEquals(MAIN.name(), form(moved.model()).base());
        assertEquals(cursor, moved.model().listCursor());
        assertEquals(List.empty(), moved.cmds());
    }

    @Test
    void aTitleReadOnceThePopupClosedOpensNothing() {
        Model closed = Update.update(creating(), new PopupMsg.Dismissed(NewPullRequestForm.class))
                .model();

        assertEquals(
                Option.none(),
                Update.update(closed, baseTitleRead(MAIN.name(), "x")).model().popup());
    }

    @Test
    void anEditOnceThePopupClosedIsIgnored() {
        Model closed = Update.update(creating(), new PopupMsg.Dismissed(NewPullRequestForm.class))
                .model();

        assertEquals(closed, Update.update(closed, typed(creating(), "x")).model());
    }

    // Confirming.

    @Test
    void aBlankTitleKeepsThePopupOpen() {
        Model blank = Update.update(creating(), typed(creating(), "  ")).model();

        Next next = Update.update(blank, confirmed());

        assertTrue(next.model().popup(NewPullRequestForm.class).isDefined());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void confirmingClosesThePopup() {
        Next next = Update.update(creating(), confirmed());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(1, next.cmds().size(), next.cmds().toString());
    }
}
