package net.vanfleteren.jazygit.feature.selection;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The selection transitions: highlighting a branch or files loads their log or diff.
 */
class SelectionUpdateTest {

    @Test
    void selectingABranchLoadsItsLogOnce() {
        Next next = Update.update(loaded(), new SelectionMsg.BranchSelected("feature"));

        assertEquals(Optional.of(new BranchLog("feature", new Loading<>())), next.model().branchLog());
        assertEquals(List.of(new LoadBranchLog("feature")), next.cmds());

        Next again = Update.update(next.model(), new SelectionMsg.BranchSelected("feature"));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void selectingACommitLoadsItsChangesOnce() {
        Next next = Update.update(loaded(), new SelectionMsg.CommitSelected("aaaa"));

        assertEquals(Optional.of(new CommitDetail("aaaa", new Loading<>())), next.model().commitDetail());
        assertEquals(List.of(new LoadCommitDetail("aaaa")), next.cmds());

        Next again = Update.update(next.model(), new SelectionMsg.CommitSelected("aaaa"));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void loadedChangesAreShownKeepingThePreviousOnesWhileTheNextCommitLoads() {
        Model first = Update.update(
                Update.update(loaded(), new SelectionMsg.CommitSelected("aaaa")).model(),
                new LoadMsg.CommitDetailLoaded("aaaa", "changes")).model();
        CommitDetail firstDetail = new CommitDetail("aaaa", new Loaded<>("changes"));
        assertEquals(Optional.of(firstDetail), first.commitDetail());

        Model second = Update.update(first, new SelectionMsg.CommitSelected("ffff")).model();
        assertEquals(Optional.of(new CommitDetail("ffff", new Loading<>(), Optional.of(firstDetail))),
                second.commitDetail());
    }

    @Test
    void changesOfACommitThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.CommitSelected("aaaa")).model();
        model = Update.update(model, new SelectionMsg.CommitSelected("ffff")).model();

        assertEquals(model.commitDetail(),
                Update.update(model, new LoadMsg.CommitDetailLoaded("aaaa", "changes")).model().commitDetail());
        assertEquals(model.commitDetail(),
                Update.update(model, new LoadMsg.Failed(new LoadCommitDetail("aaaa"), "boom")).model().commitDetail());
    }

    @Test
    void failedChangesAreShownAndRetriedOnTheNextTick() {
        Model model = Update.update(
                Update.update(loaded(), new SelectionMsg.CommitSelected("aaaa")).model(),
                new LoadMsg.Failed(new LoadCommitDetail("aaaa"), "boom")).model();
        assertEquals(Optional.of(new CommitDetail("aaaa", new Failed<>("boom"))), model.commitDetail());

        assertTrue(Update.update(model, new Tick()).cmds().contains(new LoadCommitDetail("aaaa")));
    }

    @Test
    void switchingBranchesKeepsTheLastLoadedLogUntilTheNewOneArrives() {
        Model feature = Update.update(
                Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model(),
                new LoadMsg.BranchLogLoaded("feature", FEATURE_COMMITS)).model();
        BranchLog featureLog = feature.branchLog().orElseThrow();

        Model main = Update.update(feature, new SelectionMsg.BranchSelected("main")).model();
        assertEquals(Optional.of(new BranchLog("main", new Loading<>(), Optional.of(featureLog))), main.branchLog());

        Model other = Update.update(main, new SelectionMsg.BranchSelected("other")).model();
        assertEquals(Optional.of(featureLog), other.branchLog().orElseThrow().previous());

        Model done = Update.update(other, new LoadMsg.BranchLogLoaded("other", FEATURE_COMMITS)).model();
        assertEquals(Optional.empty(), done.branchLog().orElseThrow().previous());
    }

    @Test
    void loadedLogIsShownForTheSelectedBranch() {
        Model model = Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model();

        Model next = Update.update(model, new LoadMsg.BranchLogLoaded("feature", FEATURE_COMMITS)).model();

        assertEquals(Optional.of(new BranchLog("feature", new Loaded<>(FEATURE_COMMITS))), next.branchLog());
    }

    @Test
    void resultsForABranchThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model();
        model = Update.update(model, new SelectionMsg.BranchSelected("main")).model();

        assertEquals(model.branchLog(), Update.update(model, new LoadMsg.BranchLogLoaded("feature", FEATURE_COMMITS))
                .model().branchLog());
        assertEquals(model.branchLog(), Update.update(model, new LoadMsg.Failed(new LoadBranchLog("feature"), "boom"))
                .model().branchLog());
    }

    @Test
    void failedLogIsRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model();
        model = Update.update(model, new LoadMsg.Failed(new LoadBranchLog("feature"), "boom")).model();

        assertEquals(Optional.of(new BranchLog("feature", new Failed<>("boom"))), model.branchLog());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new LoadBranchLog("feature")),
                Update.update(model, new Tick()).cmds());
    }

    @Test
    void movedTipOfTheSelectedBranchReloadsItsLog() {
        Model model = Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model();
        model = Update.update(model, new LoadMsg.BranchLogLoaded("feature", FEATURE_COMMITS)).model();

        assertEquals(List.of(), Update.update(model, new LoadMsg.BranchesLoaded(BRANCHES)).cmds());
        assertEquals(List.of(), Update.update(model, new LoadMsg.BranchesLoaded(
                List.of(new Branch("main", true, "moved"), new Branch("feature", false, "ffff")))).cmds());
        assertEquals(List.of(new LoadBranchLog("feature")), Update.update(model, new LoadMsg.BranchesLoaded(
                List.of(new Branch("main", true, "aaaa"), new Branch("feature", false, "moved")))).cmds());
    }

    @Test
    void highlightingFilesLoadsTheirDiffOnce() {
        Model model = loaded();
        List<FileEntry> files = DIRTY.files();

        Next next = Update.update(model, new SelectionMsg.FilesSelected(files));
        assertEquals(List.of(new Cmd.LoadFileDiff(files)), next.cmds());
        assertEquals(Optional.of(new FileDiff(files, new Loading<>())), next.model().fileDiff());

        assertEquals(List.of(), Update.update(next.model(), new SelectionMsg.FilesSelected(files)).cmds());
    }

    @Test
    void aDiffOfOtherFilesThanTheHighlightedOnesIsDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        List<FileEntry> other = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));

        assertEquals(model.fileDiff(), Update.update(model, new LoadMsg.FileDiffLoaded(other, new Diffs("", "x"))).model().fileDiff());
        assertEquals(Optional.of(new FileDiff(DIRTY.files(), new Loaded<>(new Diffs("", "+a")))),
                Update.update(model, new LoadMsg.FileDiffLoaded(DIRTY.files(), new Diffs("", "+a"))).model().fileDiff());
    }

    @Test
    void ticksReloadTheDiffAndKeepTheOldOneVisibleWhileLoading() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        model = Update.update(model, new LoadMsg.FileDiffLoaded(DIRTY.files(), new Diffs("", "+a"))).model();

        Next tick = Update.update(model, new Tick());
        assertEquals(true, tick.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files())));

        List<FileEntry> next = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));
        FileDiff loading = Update.update(model, new SelectionMsg.FilesSelected(next)).model().fileDiff().orElseThrow();
        assertEquals(Optional.of(new FileDiff(DIRTY.files(), new Loaded<>(new Diffs("", "+a")))), loading.previous());
    }

    @Test
    void aFailedDiffLoadIsShown() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        Cmd.LoadFileDiff cmd = new Cmd.LoadFileDiff(DIRTY.files());

        assertEquals(Optional.of(new FileDiff(DIRTY.files(), new Failed<>("boom"))),
                Update.update(model, new LoadMsg.Failed(cmd, "boom")).model().fileDiff());
    }
}
