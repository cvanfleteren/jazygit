package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The selection transitions: highlighting a branch or files loads their log or diff.
 */
class SelectionUpdateTest {

    @Test
    void selectingABranchLoadsItsLogOnce() {
        Next next = Update.update(
                loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature"))));

        assertEquals(
                Option.of(new Preview<>(new BranchRef.Local(new Branch.Name("feature")), new Loading<>())),
                next.model().repoView().branchLog());
        assertEquals(List.of(new LoadBranchLog(new BranchRef.Local(new Branch.Name("feature")))), next.cmds());

        Next again = Update.update(
                next.model(), new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature"))));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void selectingACommitLoadsItsChangesOnce() {
        Next next = Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")));

        assertEquals(
                Option.of(new Preview<>(new Sha("aaaa"), new Loading<>())),
                next.model().repoView().commitDetail());
        assertEquals(List.of(new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT)), next.cmds());

        Next again = Update.update(next.model(), new SelectionMsg.CommitSelected(new Sha("aaaa")));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void loadedChangesAreShownKeepingThePreviousOnesWhileTheNextCommitLoads() {
        Model first = Update.update(
                        Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();
        Preview<Sha, DiffText> firstDetail = new Preview<>(new Sha("aaaa"), new Loaded<>(DiffText.of("changes")));
        assertEquals(Option.of(firstDetail), first.repoView().commitDetail());

        Model second = Update.update(first, new SelectionMsg.CommitSelected(new Sha("ffff")))
                .model();
        assertEquals(
                Option.of(new Preview<>(new Sha("ffff"), new Loading<>(), Option.of(firstDetail))),
                second.repoView().commitDetail());
    }

    @Test
    void changesOfACommitThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                .model();
        model = Update.update(model, new SelectionMsg.CommitSelected(new Sha("ffff")))
                .model();

        assertEquals(
                model.repoView().commitDetail(),
                Update.update(
                                model,
                                new LoadMsg.CommitDetailLoaded(
                                        new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of("changes")))
                        .model()
                        .repoView()
                        .commitDetail());
        assertEquals(
                model.repoView().commitDetail(),
                Update.update(
                                model,
                                new LoadMsg.Failed(
                                        new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT), refused("boom")))
                        .model()
                        .repoView()
                        .commitDetail());
    }

    @Test
    void failedChangesAreShownAndRetriedOnTheNextTick() {
        Model model = Update.update(
                        Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.Failed(new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT), refused("boom")))
                .model();
        assertEquals(
                Option.of(new Preview<>(new Sha("aaaa"), new Failed<>(refused("boom")))),
                model.repoView().commitDetail());

        assertTrue(Update.update(model, new Tick(Instant.EPOCH))
                .cmds()
                .contains(new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT)));
    }

    @Test
    void switchingBranchesKeepsTheLastLoadedLogUntilTheNewOneArrives() {
        Model feature = Update.update(
                        Update.update(
                                        loaded(),
                                        new SelectionMsg.BranchSelected(
                                                new BranchRef.Local(new Branch.Name("feature"))))
                                .model(),
                        new LoadMsg.BranchLogLoaded(new BranchRef.Local(new Branch.Name("feature")), FEATURE_COMMITS))
                .model();
        Preview<BranchRef, List<Commit>> featureLog =
                feature.repoView().branchLog().get();

        Model main = Update.update(
                        feature, new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("main"))))
                .model();
        assertEquals(
                Option.of(new Preview<BranchRef, List<Commit>>(
                        new BranchRef.Local(new Branch.Name("main")), new Loading<>(), Option.of(featureLog))),
                main.repoView().branchLog());

        Model other = Update.update(
                        main, new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("other"))))
                .model();
        assertEquals(Option.of(featureLog), other.repoView().branchLog().get().previous());

        Model done = Update.update(
                        other,
                        new LoadMsg.BranchLogLoaded(new BranchRef.Local(new Branch.Name("other")), FEATURE_COMMITS))
                .model();
        assertEquals(Option.none(), done.repoView().branchLog().get().previous());
    }

    @Test
    void loadedLogIsShownForTheSelectedBranch() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature"))))
                .model();

        Model next = Update.update(
                        model,
                        new LoadMsg.BranchLogLoaded(new BranchRef.Local(new Branch.Name("feature")), FEATURE_COMMITS))
                .model();

        assertEquals(
                Option.of(
                        new Preview<>(new BranchRef.Local(new Branch.Name("feature")), new Loaded<>(FEATURE_COMMITS))),
                next.repoView().branchLog());
    }

    @Test
    void resultsForABranchThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature"))))
                .model();
        model = Update.update(model, new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("main"))))
                .model();

        assertEquals(
                model.repoView().branchLog(),
                Update.update(
                                model,
                                new LoadMsg.BranchLogLoaded(
                                        new BranchRef.Local(new Branch.Name("feature")), FEATURE_COMMITS))
                        .model()
                        .repoView()
                        .branchLog());
        assertEquals(
                model.repoView().branchLog(),
                Update.update(
                                model,
                                new LoadMsg.Failed(
                                        new LoadBranchLog(new BranchRef.Local(new Branch.Name("feature"))),
                                        refused("boom")))
                        .model()
                        .repoView()
                        .branchLog());
    }

    @Test
    void failedLogIsRetriedOnTheNextTick() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature"))))
                .model();
        model = Update.update(
                        model,
                        new LoadMsg.Failed(
                                new LoadBranchLog(new BranchRef.Local(new Branch.Name("feature"))), refused("boom")))
                .model();

        assertEquals(
                Option.of(
                        new Preview<>(new BranchRef.Local(new Branch.Name("feature")), new Failed<>(refused("boom")))),
                model.repoView().branchLog());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new Cmd.CheckMetadata(),
                        new LoadBranchLog(new BranchRef.Local(new Branch.Name("feature")))),
                Update.update(model, new Tick(Instant.EPOCH)).cmds());
    }

    @Test
    void movedTipOfTheSelectedBranchReloadsItsLog() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature"))))
                .model();
        model = Update.update(
                        model,
                        new LoadMsg.BranchLogLoaded(new BranchRef.Local(new Branch.Name("feature")), FEATURE_COMMITS))
                .model();

        assertEquals(List.of(), localBranchesRead(model, BRANCHES).cmds());
        assertEquals(
                List.of(),
                localBranchesRead(
                                model,
                                List.of(
                                        new Branch(new Branch.Name("main"), true, new Sha("moved")),
                                        new Branch(new Branch.Name("feature"), false, new Sha("ffff"))))
                        .cmds());
        assertEquals(
                List.of(new LoadBranchLog(new BranchRef.Local(new Branch.Name("feature")))),
                localBranchesRead(
                                model,
                                List.of(
                                        new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                                        new Branch(new Branch.Name("feature"), false, new Sha("moved"))))
                        .cmds());
    }

    @Test
    void highlightingFilesLoadsTheirDiffOnce() {
        Model model = loaded();
        List<FileEntry> files = DIRTY.files();

        Next next = Update.update(model, new SelectionMsg.FilesSelected(files));
        assertEquals(List.of(new Cmd.LoadFileDiff(files, DiffOptions.DEFAULT)), next.cmds());
        assertEquals(
                Option.of(new Preview<>(files, new Loading<>())),
                next.model().repoView().fileDiff());

        assertEquals(
                List.of(),
                Update.update(next.model(), new SelectionMsg.FilesSelected(files))
                        .cmds());
    }

    @Test
    void aDiffOfOtherFilesThanTheHighlightedOnesIsDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files()))
                .model();
        List<FileEntry> other = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));

        assertEquals(
                model.repoView().fileDiff(),
                Update.update(model, new LoadMsg.FileDiffLoaded(other, DiffOptions.DEFAULT, new Diffs("", "x")))
                        .model()
                        .repoView()
                        .fileDiff());
        assertEquals(
                Option.of(new Preview<>(DIRTY.files(), new Loaded<>(new Diffs("", "+a")))),
                Update.update(
                                model,
                                new LoadMsg.FileDiffLoaded(DIRTY.files(), DiffOptions.DEFAULT, new Diffs("", "+a")))
                        .model()
                        .repoView()
                        .fileDiff());
    }

    @Test
    void ticksReloadTheDiffAndKeepTheOldOneVisibleWhileLoading() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files()))
                .model();
        model = Update.update(
                        model, new LoadMsg.FileDiffLoaded(DIRTY.files(), DiffOptions.DEFAULT, new Diffs("", "+a")))
                .model();

        // The diff is read again once the status confirms that its files are still as they were.
        Model refreshed = Update.update(model, new Tick(Instant.EPOCH)).model();
        Next statusLoaded = Update.update(refreshed, new LoadMsg.StatusLoaded(DIRTY));
        assertTrue(statusLoaded.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files(), DiffOptions.DEFAULT)));

        List<FileEntry> next = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));
        Preview<List<FileEntry>, Diffs> loading = Update.update(model, new SelectionMsg.FilesSelected(next))
                .model()
                .repoView()
                .fileDiff()
                .get();
        assertEquals(Option.of(new Preview<>(DIRTY.files(), new Loaded<>(new Diffs("", "+a")))), loading.previous());
    }

    @Test
    void aDiffIsNotReloadedForFilesWhoseStatusChanged() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files()))
                .model();
        model = Update.update(
                        model, new LoadMsg.FileDiffLoaded(DIRTY.files(), DiffOptions.DEFAULT, new Diffs("", "+a")))
                .model();
        List<FileEntry> restaged =
                DIRTY.files().map(f -> new FileEntry(f.path(), f.type(), !f.staged(), !f.unstaged()));

        Next statusLoaded = Update.update(
                model,
                new LoadMsg.StatusLoaded(
                        new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), restaged)));

        assertFalse(statusLoaded.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files(), DiffOptions.DEFAULT)));
    }

    @Test
    void aFailedDiffLoadIsShown() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files()))
                .model();
        Cmd.LoadFileDiff cmd = new Cmd.LoadFileDiff(DIRTY.files(), DiffOptions.DEFAULT);

        assertEquals(
                Option.of(new Preview<>(DIRTY.files(), new Failed<>(refused("boom")))),
                Update.update(model, new LoadMsg.Failed(cmd, refused("boom")))
                        .model()
                        .repoView()
                        .fileDiff());
    }
}
