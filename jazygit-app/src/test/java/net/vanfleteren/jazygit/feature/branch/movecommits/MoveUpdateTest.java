package net.vanfleteren.jazygit.feature.branch.movecommits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitRange;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Sha;
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
 * Moving the newest commits of the checked out branch to a new branch.
 */
class MoveUpdateTest {

    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final Branch.Name MAIN = new Branch.Name("main");
    private static final Branch.Name TOPIC = new Branch.Name("topic");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));

    private static final MoveBase PARENT = new MoveBase.Parent();
    private static final MoveBase ONTO_MAIN = new MoveBase.Onto(new BranchRef.Remote(ORIGIN_MAIN));
    private static final MoveBase ONTO_UPSTREAM = new MoveBase.Onto(new BranchRef.Remote(ORIGIN_FEATURE));

    private static final CommitRange UNPUSHED = new CommitRange(2, 0, 0);
    private static final CommitRange PUSHED = new CommitRange(2, 0, 1);

    private static Commit commit(String sha, List<String> parents) {
        return new Commit(
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
                new Branch(
                        FEATURE,
                        onFeature,
                        TIP.sha(),
                        Instant.EPOCH,
                        true,
                        false,
                        Option.of(new Upstream(ORIGIN_FEATURE, 1, 0))),
                new Branch(MAIN, false, ROOT.sha(), Instant.EPOCH, true, true));
        Model model = TestModels.loaded();
        model = Update.update(model, new LoadMsg.BranchesLoaded(branches)).model();
        model = Update.update(
                        model,
                        new LoadMsg.RemoteBranchesLoaded(List.of(
                                new RemoteRef(ORIGIN_MAIN, ROOT.sha(), Instant.EPOCH),
                                new RemoteRef(ORIGIN_FEATURE, MIDDLE.sha(), Instant.EPOCH))))
                .model();
        return Update.update(model, new LoadMsg.CommitsLoaded(List.of(TIP, MIDDLE, ROOT)))
                .model();
    }

    private static Model named(CommitRange range) {
        Model read = Update.update(model(true), new MoveMsg.RangeRead(FEATURE, MIDDLE.sha(), range))
                .model();
        return Update.update(read, new MoveMsg.NameConfirmed("topic")).model();
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
                Option.of(new AppError.NoBranchCheckedOut()),
                next.model().outcome().error());
    }

    @Test
    void theRangeAsksForTheNameWithTheBases() {
        Model model = Update.update(model(true), new MoveMsg.RangeRead(FEATURE, MIDDLE.sha(), UNPUSHED))
                .model();

        assertEquals(
                Option.of(List.of(PARENT, ONTO_MAIN, ONTO_UPSTREAM)),
                model.popup(MoveNamePopup.class).map(popup -> popup.request().bases()));
    }

    @Test
    void commitsWithAMergeOnlyMoveWhereTheyAre() {
        Next next = Update.update(
                Update.update(model(true), new MoveMsg.RangeRead(FEATURE, MIDDLE.sha(), new CommitRange(2, 1, 0)))
                        .model(),
                new MoveMsg.NameConfirmed("topic"));

        assertEquals(
                List.of(new MoveCmd.Move(FEATURE, MIDDLE.sha(), TOPIC, Option.none(), Option.none())), next.cmds());
        assertEquals(Option.none(), next.model().popup(MoveBasePopup.class));
    }

    @Test
    void theNameOfABranchThatExistsKeepsTheDialogOpen() {
        Model read = Update.update(model(true), new MoveMsg.RangeRead(FEATURE, MIDDLE.sha(), UNPUSHED))
                .model();

        Next next = Update.update(read, new MoveMsg.NameConfirmed("main"));

        assertTrue(next.model().popup(MoveNamePopup.class).isDefined());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void choosingABaseCopiesTheCommitsOntoIt() {
        Next next = Update.update(named(UNPUSHED), new MoveMsg.BaseChosen(ONTO_MAIN));

        assertEquals(
                List.of(new MoveCmd.Move(
                        FEATURE, MIDDLE.sha(), TOPIC, Option.of(new BranchRef.Remote(ORIGIN_MAIN)), Option.none())),
                next.cmds());
        assertEquals(Option.none(), next.model().popup(MoveBasePopup.class));
    }

    @Test
    void pushedCommitsAskWhetherToForcePush() {
        Model asked =
                Update.update(named(PUSHED), new MoveMsg.BaseChosen(PARENT)).model();

        Next next = Update.update(asked, new MoveMsg.PushedConfirmed(true));

        assertEquals(
                List.of(new MoveCmd.Move(FEATURE, MIDDLE.sha(), TOPIC, Option.none(), Option.of(ORIGIN_FEATURE))),
                next.cmds());
        assertEquals(Option.none(), next.model().popup(MovePushedPopup.class));
    }

    @Test
    void pushedCommitsCanMoveWithoutAPush() {
        Model asked =
                Update.update(named(PUSHED), new MoveMsg.BaseChosen(PARENT)).model();

        Next next = Update.update(asked, new MoveMsg.PushedConfirmed(false));

        assertEquals(
                List.of(new MoveCmd.Move(FEATURE, MIDDLE.sha(), TOPIC, Option.none(), Option.none())), next.cmds());
    }

    @Test
    void movedCommitsForcePushTheOldBranchWhenAsked() {
        MoveCmd.Move cmd = new MoveCmd.Move(FEATURE, MIDDLE.sha(), TOPIC, Option.none(), Option.of(ORIGIN_FEATURE));

        Next next = Update.update(model(true), new MoveMsg.Moved(cmd, List.of()));

        assertTrue(next.cmds().contains(new PushCmd.Push(FEATURE, ORIGIN_FEATURE, PushMode.FORCE_WITH_LEASE)));
    }

    @Test
    void aStopWhileCopyingSaysTheOldBranchStillHasTheCommits() {
        MoveCmd.Move cmd = new MoveCmd.Move(
                FEATURE, MIDDLE.sha(), TOPIC, Option.of(new BranchRef.Remote(ORIGIN_MAIN)), Option.none());
        Notice.StoppedOnConflicts stopped =
                new Notice.StoppedOnConflicts(Option.of(new InProgress.CherryPicking(MIDDLE.sha())), 1);

        Next next = Update.update(model(true), new MoveMsg.Stopped(cmd, List.of(), stopped));

        assertEquals(
                Option.of(new Notice.MoveStopped(stopped, FEATURE, MIDDLE.sha())),
                next.model().outcome().notice());
    }
}
