package net.vanfleteren.jazygit.feature.selection;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.BranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.CommitDetail;
import net.vanfleteren.jazygit.state.FileDiff;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

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

        assertEquals(Option.of(new BranchLog("feature", new Loading<>())), next.model().branchLog());
        assertEquals(List.of(new LoadBranchLog("feature")), next.cmds());

        Next again = Update.update(next.model(), new SelectionMsg.BranchSelected("feature"));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void selectingACommitLoadsItsChangesOnce() {
        Next next = Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")));

        assertEquals(Option.of(new CommitDetail(new Sha("aaaa"), new Loading<>())), next.model().commitDetail());
        assertEquals(List.of(new LoadCommitDetail(new Sha("aaaa"))), next.cmds());

        Next again = Update.update(next.model(), new SelectionMsg.CommitSelected(new Sha("aaaa")));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void loadedChangesAreShownKeepingThePreviousOnesWhileTheNextCommitLoads() {
        Model first = Update.update(
                Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa"))).model(),
                new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), "changes")).model();
        CommitDetail firstDetail = new CommitDetail(new Sha("aaaa"), new Loaded<>("changes"));
        assertEquals(Option.of(firstDetail), first.commitDetail());

        Model second = Update.update(first, new SelectionMsg.CommitSelected(new Sha("ffff"))).model();
        assertEquals(Option.of(new CommitDetail(new Sha("ffff"), new Loading<>(), Option.of(firstDetail))),
                second.commitDetail());
    }

    @Test
    void changesOfACommitThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa"))).model();
        model = Update.update(model, new SelectionMsg.CommitSelected(new Sha("ffff"))).model();

        assertEquals(model.commitDetail(),
                Update.update(model, new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), "changes")).model().commitDetail());
        assertEquals(model.commitDetail(),
                Update.update(model, new LoadMsg.Failed(new LoadCommitDetail(new Sha("aaaa")), "boom")).model().commitDetail());
    }

    @Test
    void failedChangesAreShownAndRetriedOnTheNextTick() {
        Model model = Update.update(
                Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa"))).model(),
                new LoadMsg.Failed(new LoadCommitDetail(new Sha("aaaa")), "boom")).model();
        assertEquals(Option.of(new CommitDetail(new Sha("aaaa"), new Failed<>("boom"))), model.commitDetail());

        assertTrue(Update.update(model, new Tick()).cmds().contains(new LoadCommitDetail(new Sha("aaaa"))));
    }

    @Test
    void switchingBranchesKeepsTheLastLoadedLogUntilTheNewOneArrives() {
        Model feature = Update.update(
                Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model(),
                new LoadMsg.BranchLogLoaded("feature", FEATURE_COMMITS)).model();
        BranchLog featureLog = feature.branchLog().get();

        Model main = Update.update(feature, new SelectionMsg.BranchSelected("main")).model();
        assertEquals(Option.of(new BranchLog("main", new Loading<>(), Option.of(featureLog))), main.branchLog());

        Model other = Update.update(main, new SelectionMsg.BranchSelected("other")).model();
        assertEquals(Option.of(featureLog), other.branchLog().get().previous());

        Model done = Update.update(other, new LoadMsg.BranchLogLoaded("other", FEATURE_COMMITS)).model();
        assertEquals(Option.none(), done.branchLog().get().previous());
    }

    @Test
    void loadedLogIsShownForTheSelectedBranch() {
        Model model = Update.update(loaded(), new SelectionMsg.BranchSelected("feature")).model();

        Model next = Update.update(model, new LoadMsg.BranchLogLoaded("feature", FEATURE_COMMITS)).model();

        assertEquals(Option.of(new BranchLog("feature", new Loaded<>(FEATURE_COMMITS))), next.branchLog());
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

        assertEquals(Option.of(new BranchLog("feature", new Failed<>("boom"))), model.branchLog());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new net.vanfleteren.jazygit.state.Cmd.LoadStashes(), new LoadBranchLog("feature")),
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
        assertEquals(Option.of(new FileDiff(files, new Loading<>())), next.model().fileDiff());

        assertEquals(List.of(), Update.update(next.model(), new SelectionMsg.FilesSelected(files)).cmds());
    }

    @Test
    void aDiffOfOtherFilesThanTheHighlightedOnesIsDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        List<FileEntry> other = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));

        assertEquals(model.fileDiff(), Update.update(model, new LoadMsg.FileDiffLoaded(other, new Diffs("", "x"))).model().fileDiff());
        assertEquals(Option.of(new FileDiff(DIRTY.files(), new Loaded<>(new Diffs("", "+a")))),
                Update.update(model, new LoadMsg.FileDiffLoaded(DIRTY.files(), new Diffs("", "+a"))).model().fileDiff());
    }

    @Test
    void ticksReloadTheDiffAndKeepTheOldOneVisibleWhileLoading() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        model = Update.update(model, new LoadMsg.FileDiffLoaded(DIRTY.files(), new Diffs("", "+a"))).model();

        // The diff is read again once the status confirms that its files are still as they were.
        Model refreshed = Update.update(model, new Tick()).model();
        Next statusLoaded = Update.update(refreshed, new LoadMsg.StatusLoaded(DIRTY));
        assertEquals(true, statusLoaded.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files())));

        List<FileEntry> next = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));
        FileDiff loading = Update.update(model, new SelectionMsg.FilesSelected(next)).model().fileDiff().get();
        assertEquals(Option.of(new FileDiff(DIRTY.files(), new Loaded<>(new Diffs("", "+a")))), loading.previous());
    }

    @Test
    void aDiffIsNotReloadedForFilesWhoseStatusChanged() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        model = Update.update(model, new LoadMsg.FileDiffLoaded(DIRTY.files(), new Diffs("", "+a"))).model();
        List<FileEntry> restaged = DIRTY.files()
                .map(f -> new FileEntry(f.path(), f.type(), !f.staged(), !f.unstaged()));

        Next statusLoaded = Update.update(model, new LoadMsg.StatusLoaded(new RepoStatus("main", "aaaa", restaged)));

        assertEquals(false, statusLoaded.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files())));
    }

    @Test
    void aFailedDiffLoadIsShown() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files())).model();
        Cmd.LoadFileDiff cmd = new Cmd.LoadFileDiff(DIRTY.files());

        assertEquals(Option.of(new FileDiff(DIRTY.files(), new Failed<>("boom"))),
                Update.update(model, new LoadMsg.Failed(cmd, "boom")).model().fileDiff());
    }
}
