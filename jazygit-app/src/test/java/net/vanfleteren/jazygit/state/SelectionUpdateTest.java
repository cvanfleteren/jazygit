package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadStashDetail;
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

    private static final LoadCommitDetail DETAIL = new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT);

    @Test
    void selectingABranchLoadsItsLogOnce() {
        Next next = Update.update(
                loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))));

        assertEquals(
                Option.of(new Preview<>(new BranchRef.Local(new BranchName("feature")), new Loading<>())),
                next.model().repoView().branchLog());
        assertEquals(List.of(TestModels.loadBranchLog(new BranchRef.Local(new BranchName("feature")))), next.cmds());

        Next again = Update.update(
                next.model(), new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))));
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

        assertTrue(Update.update(model, new Tick())
                .cmds()
                .contains(new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT)));
    }

    @Test
    void changesThatKeepFailingAreTriedAgainLessAndLessOften() {
        Model failed = failedDetail(selectedCommit());

        // Each tick that tries again fails again; it waits 1, 2, 4 and then 8 ticks.
        List<Integer> tried = List.rangeClosed(1, 16)
                .foldLeft(Tuple.of(failed, List.<Integer>empty()), (state, tick) -> {
                    Next next = Update.tick(state._1());
                    return next.cmds().contains(DETAIL)
                            ? Tuple.of(failedDetail(next.model()), state._2().append(tick))
                            : Tuple.of(next.model(), state._2());
                })
                ._2();

        assertEquals(List.of(1, 3, 7, 15), tried);
    }

    @Test
    void changesThatFailedAreTriedAgainAtOnceOnARefreshAndForgottenOnceRead() {
        Model failedTwice =
                failedDetail(Update.tick(failedDetail(selectedCommit())).model());

        // The next tick that tries again is two ticks off.
        assertFalse(Update.tick(failedTwice).cmds().contains(DETAIL));
        assertTrue(Update.refresh(failedTwice).cmds().contains(DETAIL));

        Model read = Update.update(
                        failedTwice,
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();
        assertEquals(HashMap.empty(), read.jobs().retries());
    }

    @Test
    void aSkippedLoadIsNoLongerRunningAndIsNoFailure() {
        Model running = TestModels.running(failedDetail(failedDetail(selectedCommit())), DETAIL);

        Model skipped =
                TestModels.done(running, DETAIL, new LoadMsg.Skipped(DETAIL)).model();

        assertFalse(skipped.jobs().runs(DETAIL));
        // The backoff of a retry that a newer highlight skipped stays.
        assertEquals(running.jobs().retries(), skipped.jobs().retries());
        assertNotEquals(HashMap.empty(), skipped.jobs().retries());
    }

    private static Model selectedCommit() {
        return Update.update(loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                .model();
    }

    private static Model failedDetail(Model model) {
        return Update.update(model, new LoadMsg.Failed(DETAIL, refused("boom"))).model();
    }

    @Test
    void eachViewReadsTheCommitItHighlightsInItsOwnSlot() {
        assertEquals(
                Option.of(Cmd.Slot.COMMIT_DETAIL), new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT).slot());
        assertEquals(
                Option.of(Cmd.Slot.DIVERGENCE_DETAIL),
                new LoadCommitDetail(new Sha("aaaa"), DiffOptions.DEFAULT, Cmd.Slot.DIVERGENCE_DETAIL).slot());
    }

    @Test
    void selectingAStashLoadsItsChangesOnce() {
        Next next = Update.update(loaded(), new SelectionMsg.StashSelected(new Sha("5555")));

        assertEquals(
                Option.of(new Preview<>(new Sha("5555"), new Loading<>())),
                next.model().repoView().stashDetail());
        assertEquals(List.of(new LoadStashDetail(new Sha("5555"), DiffOptions.DEFAULT)), next.cmds());

        Next again = Update.update(next.model(), new SelectionMsg.StashSelected(new Sha("5555")));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void changesOfAStashThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(loaded(), new SelectionMsg.StashSelected(new Sha("5555")))
                .model();
        model = Update.update(model, new SelectionMsg.StashSelected(new Sha("6666")))
                .model();

        assertEquals(
                model.repoView().stashDetail(),
                Update.update(
                                model,
                                new LoadMsg.StashDetailLoaded(
                                        new Sha("5555"), DiffOptions.DEFAULT, DiffText.of("changes")))
                        .model()
                        .repoView()
                        .stashDetail());
        assertEquals(
                model.repoView().stashDetail(),
                Update.update(
                                model,
                                new LoadMsg.Failed(
                                        new LoadStashDetail(new Sha("5555"), DiffOptions.DEFAULT), refused("boom")))
                        .model()
                        .repoView()
                        .stashDetail());
    }

    @Test
    void loadedStashChangesAreShownAndFailedOnesRetriedOnTheNextTick() {
        Model selected = Update.update(loaded(), new SelectionMsg.StashSelected(new Sha("5555")))
                .model();
        assertEquals(
                Option.of(new Preview<>(new Sha("5555"), new Loaded<>(DiffText.of("changes")))),
                Update.update(
                                selected,
                                new LoadMsg.StashDetailLoaded(
                                        new Sha("5555"), DiffOptions.DEFAULT, DiffText.of("changes")))
                        .model()
                        .repoView()
                        .stashDetail());

        Model failed = Update.update(
                        selected,
                        new LoadMsg.Failed(new LoadStashDetail(new Sha("5555"), DiffOptions.DEFAULT), refused("boom")))
                .model();
        assertEquals(
                Option.of(new Preview<>(new Sha("5555"), new Failed<>(refused("boom")))),
                failed.repoView().stashDetail());
        assertTrue(Update.update(failed, new Tick())
                .cmds()
                .contains(new LoadStashDetail(new Sha("5555"), DiffOptions.DEFAULT)));
    }

    @Test
    void switchingBranchesKeepsTheLastLoadedLogUntilTheNewOneArrives() {
        Model feature = Update.update(
                        Update.update(
                                        loaded(),
                                        new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))))
                                .model(),
                        TestModels.branchLogLoaded(new BranchRef.Local(new BranchName("feature")), FEATURE_COMMITS))
                .model();
        Preview<BranchRef, List<Commit>> featureLog =
                feature.repoView().branchLog().get();

        Model main = Update.update(
                        feature, new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("main"))))
                .model();
        assertEquals(
                Option.of(new Preview<BranchRef, List<Commit>>(
                        new BranchRef.Local(new BranchName("main")), new Loading<>(), Option.of(featureLog))),
                main.repoView().branchLog());

        Model other = Update.update(main, new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("other"))))
                .model();
        assertEquals(Option.of(featureLog), other.repoView().branchLog().get().previous());

        Model done = Update.update(
                        other,
                        TestModels.branchLogLoaded(new BranchRef.Local(new BranchName("other")), FEATURE_COMMITS))
                .model();
        assertEquals(Option.none(), done.repoView().branchLog().get().previous());
    }

    @Test
    void loadedLogIsShownForTheSelectedBranch() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))))
                .model();

        Model next = Update.update(
                        model,
                        TestModels.branchLogLoaded(new BranchRef.Local(new BranchName("feature")), FEATURE_COMMITS))
                .model();

        assertEquals(
                Option.of(new Preview<>(new BranchRef.Local(new BranchName("feature")), new Loaded<>(FEATURE_COMMITS))),
                next.repoView().branchLog());
    }

    @Test
    void resultsForABranchThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))))
                .model();
        model = Update.update(model, new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("main"))))
                .model();

        assertEquals(
                model.repoView().branchLog(),
                Update.update(
                                model,
                                TestModels.branchLogLoaded(
                                        new BranchRef.Local(new BranchName("feature")), FEATURE_COMMITS))
                        .model()
                        .repoView()
                        .branchLog());
        assertEquals(
                model.repoView().branchLog(),
                Update.update(
                                model,
                                new LoadMsg.Failed(
                                        TestModels.loadBranchLog(new BranchRef.Local(new BranchName("feature"))),
                                        refused("boom")))
                        .model()
                        .repoView()
                        .branchLog());
    }

    @Test
    void failedLogIsRetriedOnTheNextTick() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))))
                .model();
        model = Update.update(
                        model,
                        new LoadMsg.Failed(
                                TestModels.loadBranchLog(new BranchRef.Local(new BranchName("feature"))),
                                refused("boom")))
                .model();

        assertEquals(
                Option.of(new Preview<>(new BranchRef.Local(new BranchName("feature")), new Failed<>(refused("boom")))),
                model.repoView().branchLog());
        assertEquals(
                List.of(
                        new LoadStatus(),
                        new Cmd.CheckMetadata(),
                        TestModels.loadBranchLog(new BranchRef.Local(new BranchName("feature")))),
                Update.update(model, new Tick()).cmds());
    }

    @Test
    void aRetriedLogThatCameBackIsNoLongerRunning() {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        Model selected = Update.update(loaded(), new SelectionMsg.BranchSelected(feature))
                .model();
        Model failed = Update.update(selected, new LoadMsg.Failed(TestModels.loadBranchLog(feature), refused("boom")))
                .model();
        Model retrying = TestModels.stepped(failed, new Tick()).model();
        assertTrue(retrying.jobs().runs(TestModels.loadBranchLog(feature)));

        Model retried = TestModels.done(
                        retrying,
                        TestModels.loadBranchLog(feature),
                        TestModels.branchLogLoaded(feature, FEATURE_COMMITS))
                .model();
        assertFalse(retried.jobs().runs(TestModels.loadBranchLog(feature)));
    }

    @Test
    void movedTipOfTheSelectedBranchReloadsItsLog() {
        Model model = Update.update(
                        loaded(), new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature"))))
                .model();
        model = Update.update(
                        model,
                        TestModels.branchLogLoaded(new BranchRef.Local(new BranchName("feature")), FEATURE_COMMITS))
                .model();

        assertEquals(List.of(), localBranchesRead(model, BRANCHES).cmds());
        assertEquals(
                List.of(),
                localBranchesRead(
                                model,
                                List.of(
                                        Fixtures.branch(new BranchName("main"), true, new Sha("moved")),
                                        Fixtures.branch(new BranchName("feature"), false, new Sha("ffff"))))
                        .cmds());
        assertEquals(
                List.of(TestModels.loadBranchLog(new BranchRef.Local(new BranchName("feature")))),
                localBranchesRead(
                                model,
                                List.of(
                                        Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
                                        Fixtures.branch(new BranchName("feature"), false, new Sha("moved"))))
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
        List<FileEntry> other = List.of(Fixtures.fileEntry("b.txt", ChangeType.UNTRACKED));

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
        Model refreshed = Update.update(model, new Tick()).model();
        Next statusLoaded = Update.update(refreshed, new LoadMsg.StatusLoaded(DIRTY));
        assertTrue(statusLoaded.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files(), DiffOptions.DEFAULT)));

        List<FileEntry> next = List.of(Fixtures.fileEntry("b.txt", ChangeType.UNTRACKED));
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
                        new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), restaged)));

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
