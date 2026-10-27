package net.vanfleteren.jazygit.feature.branch.movecommits;

import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.BranchRefusal;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Notice;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Moving the newest commits of the checked out branch to a new or an existing branch.
 */
class MoveUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName TOPIC = new BranchName("topic");
    private static final BranchName ELSEWHERE = new BranchName("elsewhere");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));

    private static final MoveBase PARENT = new MoveBase.Parent();
    private static final MoveBase ONTO_MAIN = new MoveBase.Onto(new BranchRef.Remote(ORIGIN_MAIN));
    private static final MoveBase ONTO_UPSTREAM = new MoveBase.Onto(new BranchRef.Remote(ORIGIN_FEATURE));

    private static final CommitRange UNPUSHED = new CommitRange(2, 0, 0, Option.none());
    private static final CommitRange PUSHED = new CommitRange(2, 0, 1, Option.none());

    private static Commit commit(String sha, List<String> parents) {
        return Fixtures.commit(
                new Sha(sha),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                "Commit " + sha,
                "",
                parents.map(Sha::new).toList());
    }

    private static Model model(boolean onFeature) {
        List<Branch> branches = List.of(
                Fixtures.branch(
                        FEATURE,
                        onFeature,
                        TIP.sha(),
                        Instant.EPOCH,
                        true,
                        false,
                        Option.of(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 1, 0))),
                Fixtures.branch(MAIN, false, ROOT.sha(), Instant.EPOCH, true, true),
                new Branch(
                        ELSEWHERE,
                        false,
                        ROOT.sha(),
                        Instant.EPOCH,
                        false,
                        false,
                        new Tracking.None(),
                        Option.of(Path.of("/elsewhere"))));
        Model model = TestModels.loaded();
        model = TestModels.localBranchesRead(model, branches).model();
        model = TestModels.remoteBranchesRead(
                        model,
                        List.of(
                                new RemoteRef(ORIGIN_MAIN, ROOT.sha(), Instant.EPOCH),
                                new RemoteRef(ORIGIN_FEATURE, MIDDLE.sha(), Instant.EPOCH)))
                .model();
        return Update.update(model, TestModels.commitsLoaded(List.of(TIP, MIDDLE, ROOT)))
                .model();
    }

    private static Model read(CommitRange range) {
        return Update.update(model(true), new MoveMsg.RangeRead(FEATURE, MIDDLE.sha(), range))
                .model();
    }

    private static Model newBranch(CommitRange range) {
        return Update.update(read(range), new MoveMsg.TargetChosen(Option.none()))
                .model();
    }

    private static Model named(CommitRange range) {
        return Update.update(typed(newBranch(range), "topic"), new MoveMsg.NameConfirmed())
                .model();
    }

    private static MoveCmd.Move toTopic(Option<BranchRef> onto, Option<RemoteBranch> forcePush) {
        return new MoveCmd.Move(FEATURE, MIDDLE.sha(), new MoveTarget.NewBranch(TOPIC, onto), forcePush);
    }

    private static MoveCmd.Move toMain() {
        return new MoveCmd.Move(FEATURE, MIDDLE.sha(), new MoveTarget.Existing(MAIN), Option.none());
    }

    /**
     * {@code model} with a change in the working tree.
     */
    private static Model changed(Model model) {
        return Update.update(
                        model,
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(FEATURE, TIP.sha()),
                                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();
    }

    @Test
    void requestReadsWhichCommitsMove() {
        Next next = Update.update(model(true), new MoveMsg.Requested(MIDDLE.sha()));

        assertEquals(List.of(new MoveCmd.ReadRange(FEATURE, MIDDLE.sha(), Option.of(ORIGIN_FEATURE))), next.cmds());
    }

    @Test
    void theFirstCommitCannotBeMoved() {
        Next next = Update.update(model(true), new MoveMsg.Requested(ROOT.sha()));

        assertEquals(List.of(), next.cmds());
    }

    @Test
    void withoutABranchCheckedOutNothingMoves() {
        Next next = Update.update(model(false), new MoveMsg.Requested(MIDDLE.sha()));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of(new BranchRefusal.NoBranchCheckedOut()),
                next.model().outcome().error());
    }

    @Test
    void theRangeAsksWhereTheCommitsGoAmongTheOtherBranches() {
        assertEquals(
                Option.of(List.of(MAIN, ELSEWHERE)),
                read(UNPUSHED)
                        .popup(MoveTargetPopup.class)
                        .map(popup -> popup.branches().map(Branch::name)));
    }

    @Test
    void aRangeOfAnotherBranchThanTheCheckedOutOneOrOfNoCommitsAsksNothing() {
        Model model = model(true);

        assertEquals(
                model,
                Update.update(model, new MoveMsg.RangeRead(MAIN, MIDDLE.sha(), UNPUSHED))
                        .model());
        assertEquals(
                model,
                Update.update(
                                model,
                                new MoveMsg.RangeRead(FEATURE, MIDDLE.sha(), new CommitRange(0, 0, 0, Option.none())))
                        .model());
    }

    @Test
    void aNewBranchAsksForItsNameWithTheBases() {
        assertEquals(
                Option.of(List.of(PARENT, ONTO_MAIN, ONTO_UPSTREAM)),
                newBranch(UNPUSHED)
                        .popup(MoveNamePopup.class)
                        .map(popup -> popup.request().bases()));
    }

    @Test
    void commitsWithAMergeOnlyMoveToANewBranchWhereTheyAre() {
        Model asked = read(new CommitRange(2, 1, 0, Option.none()));

        Next next = Update.update(typed(asked, "topic"), new MoveMsg.NameConfirmed());

        assertEquals(Option.none(), asked.popup(MoveTargetPopup.class));
        assertEquals(List.of(toTopic(Option.none(), Option.none())), next.cmds());
        assertEquals(Option.none(), next.model().popup(MoveBasePopup.class));
    }

    @Test
    void anOldestCommitThatCameInThroughAMergeIsRefused() {
        Model refused = read(new CommitRange(2, 1, 0, Option.of(TIP.sha())));

        assertEquals(Option.none(), refused.popup(MoveTargetPopup.class));
        assertEquals(Option.none(), refused.popup(MoveNamePopup.class));
        assertEquals(
                Option.of(new BranchRefusal.MovedThroughMerge(MIDDLE.sha(), FEATURE, TIP.sha(), ROOT.sha())),
                refused.outcome().error());
    }

    @Test
    void theNameOfABranchThatExistsKeepsTheDialogOpen() {
        Next next = Update.update(typed(newBranch(UNPUSHED), "main"), new MoveMsg.NameConfirmed());

        assertTrue(next.model().popup(MoveNamePopup.class).isDefined());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void choosingABaseCopiesTheCommitsOntoIt() {
        Next next = Update.update(named(UNPUSHED), new MoveMsg.BaseChosen(ONTO_MAIN));

        assertEquals(List.of(toTopic(Option.of(new BranchRef.Remote(ORIGIN_MAIN)), Option.none())), next.cmds());
        assertEquals(Option.none(), next.model().popup(MoveBasePopup.class));
    }

    @Test
    void choosingAnExistingBranchCopiesTheCommitsOntoIt() {
        Next next = Update.update(read(UNPUSHED), new MoveMsg.TargetChosen(Option.of(MAIN)));

        assertEquals(List.of(toMain()), next.cmds());
        assertEquals(Option.none(), next.model().popup(MoveTargetPopup.class));
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeIsRefused() {
        Next next = Update.update(read(UNPUSHED), new MoveMsg.TargetChosen(Option.of(ELSEWHERE)));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of(new AppError.CheckedOutElsewhere(MoveCmd.MOVE, ELSEWHERE, "/elsewhere")),
                next.model().outcome().error());
    }

    @Test
    void changesKeepCommitsFromBeingCopiedOntoAnExistingBranch() {
        Next next = Update.update(changed(read(UNPUSHED)), new MoveMsg.TargetChosen(Option.of(MAIN)));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of(new BranchRefusal.ChangesBeforeMove("main")),
                next.model().outcome().error());
    }

    @Test
    void changesKeepCommitsFromBeingCopiedOntoTheBaseOfANewBranch() {
        Next next = Update.update(changed(named(UNPUSHED)), new MoveMsg.BaseChosen(ONTO_MAIN));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of(new BranchRefusal.ChangesBeforeMove("origin/main")),
                next.model().outcome().error());
    }

    @Test
    void changesDoNotKeepCommitsFromMovingWhereTheyAre() {
        Next next = Update.update(changed(named(UNPUSHED)), new MoveMsg.BaseChosen(PARENT));

        assertEquals(List.of(toTopic(Option.none(), Option.none())), next.cmds());
    }

    @Test
    void untrackedFilesDoNotKeepCommitsFromBeingCopied() {
        Model untracked = Update.update(
                        read(UNPUSHED),
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(FEATURE, TIP.sha()),
                                List.of(Fixtures.fileEntry("new.txt", ChangeType.UNTRACKED)))))
                .model();

        Next next = Update.update(untracked, new MoveMsg.TargetChosen(Option.of(MAIN)));

        assertEquals(List.of(toMain()), next.cmds());
    }

    @Test
    void pushedCommitsAskWhetherToForcePush() {
        Model asked =
                Update.update(named(PUSHED), new MoveMsg.BaseChosen(PARENT)).model();

        Next next = Update.update(asked, new MoveMsg.PushedConfirmed(true));

        assertEquals(List.of(toTopic(Option.none(), Option.of(ORIGIN_FEATURE))), next.cmds());
        assertEquals(Option.none(), next.model().popup(MovePushedPopup.class));
    }

    @Test
    void pushedCommitsCanMoveWithoutAPush() {
        Model asked =
                Update.update(named(PUSHED), new MoveMsg.BaseChosen(PARENT)).model();

        Next next = Update.update(asked, new MoveMsg.PushedConfirmed(false));

        assertEquals(List.of(toTopic(Option.none(), Option.none())), next.cmds());
    }

    @Test
    void pushedCommitsMovingToAnExistingBranchAskToo() {
        Model asked = Update.update(read(PUSHED), new MoveMsg.TargetChosen(Option.of(MAIN)))
                .model();

        assertEquals(
                Option.of(new MoveTarget.Existing(MAIN)),
                asked.popup(MovePushedPopup.class).map(MovePushedPopup::target));
    }

    @Test
    void movedCommitsForcePushTheOldBranchWhenAsked() {
        Next next = Update.update(
                model(true), new MoveMsg.Moved(toTopic(Option.none(), Option.of(ORIGIN_FEATURE)), List.of()));

        assertTrue(next.cmds().contains(new PushCmd.Push(FEATURE, ORIGIN_FEATURE, PushMode.FORCE_WITH_LEASE)));
    }

    @Test
    void aStopWhileCopyingSaysTheOldBranchStillHasTheCommits() {
        Notice.StoppedOnConflicts stopped =
                new Notice.StoppedOnConflicts(Option.of(new InProgress.CherryPicking(MIDDLE.sha())), 1);

        Next next = Update.update(model(true), new MoveMsg.Stopped(toMain(), List.of(), stopped));

        assertEquals(
                Option.of(new Notice.MoveStopped(stopped, FEATURE, MIDDLE.sha(), MAIN)),
                next.model().outcome().notice());
    }
}
