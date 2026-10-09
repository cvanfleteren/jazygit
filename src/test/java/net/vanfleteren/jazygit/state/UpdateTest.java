package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.ChangeType;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.FileEntry;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.Checkout;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Cmd.Stage;
import net.vanfleteren.jazygit.state.Cmd.Unstage;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Msg.BranchLogLoaded;
import net.vanfleteren.jazygit.state.Msg.BranchSelected;
import net.vanfleteren.jazygit.state.Msg.BranchesLoaded;
import net.vanfleteren.jazygit.state.Msg.CheckedOut;
import net.vanfleteren.jazygit.state.Msg.CheckoutFailed;
import net.vanfleteren.jazygit.state.Msg.CheckoutRequested;
import net.vanfleteren.jazygit.state.Msg.CommitsLoaded;
import net.vanfleteren.jazygit.state.Msg.LoadFailed;
import net.vanfleteren.jazygit.state.Msg.StageToggleFailed;
import net.vanfleteren.jazygit.state.Msg.StageToggled;
import net.vanfleteren.jazygit.state.Msg.StatusLoaded;
import net.vanfleteren.jazygit.state.Msg.Tick;
import net.vanfleteren.jazygit.state.Msg.ToggleStageRequested;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The state transitions, tested as plain functions: no git, no threads, no terminal.
 */
class UpdateTest {

    private static final RepoStatus CLEAN = new RepoStatus("main", "aaaa", List.of());
    private static final RepoStatus DIRTY = new RepoStatus("main", "aaaa",
            List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
    private static final RepoStatus MOVED = new RepoStatus("main", "bbbb", List.of());
    private static final List<Branch> BRANCHES = List.of(new Branch("main", true, "aaaa"),
            new Branch("feature", false, "ffff"));
    private static final List<Commit> COMMITS = List.of(
            new Commit("aaaa", "Ada", "ada@example.com", Instant.EPOCH, "First", ""));
    private static final List<Commit> FEATURE_COMMITS = List.of(
            new Commit("ffff", "Ada", "ada@example.com", Instant.EPOCH, "Feature", ""));

    @Test
    void initStartsLoadingStatusAndBranches() {
        Next next = Update.init("repo");

        assertEquals("repo", next.model().repositoryName());
        assertInstanceOf(Loading.class, next.model().status());
        assertInstanceOf(Loading.class, next.model().commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches()), next.cmds());
    }

    @Test
    void firstStatusLoadsCommits() {
        Next next = Update.update(Update.init("repo").model(), new StatusLoaded(CLEAN));

        assertEquals(new Loaded<>(CLEAN), next.model().status());
        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void tickDoesNotRepeatLoadsThatAreStillRunning() {
        Model model = Update.init("repo").model();

        assertEquals(List.of(), Update.update(model, new Tick()).cmds());

        Model statusDone = Update.update(model, new StatusLoaded(CLEAN)).model();
        assertEquals(List.of(new LoadStatus()), Update.update(statusDone, new Tick()).cmds());
    }

    @Test
    void unchangedStatusKeepsTheSameInstanceAndDoesNotReloadCommits() {
        Model model = loaded();

        Next next = Update.update(model, new StatusLoaded(new RepoStatus("main", "aaaa", List.of())));

        assertSame(model.status(), next.model().status());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void workingTreeChangeReplacesStatusButNotCommits() {
        Model model = loaded();

        Next next = Update.update(model, new StatusLoaded(DIRTY));

        assertEquals(new Loaded<>(DIRTY), next.model().status());
        assertSame(model.commits(), next.model().commits());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void movedHeadReloadsCommits() {
        Next next = Update.update(loaded(), new StatusLoaded(MOVED));

        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void unchangedBranchesAndCommitsKeepTheirInstances() {
        Model model = loaded();

        assertSame(model.branches(),
                Update.update(model, new BranchesLoaded(List.copyOf(BRANCHES))).model().branches());
        assertSame(model.commits(),
                Update.update(model, new CommitsLoaded(List.copyOf(COMMITS))).model().commits());
    }

    @Test
    void failuresAreStoredAndRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new LoadFailed(new LoadCommits(), "boom")).model();
        assertEquals(new Failed<>("boom"), model.commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new LoadCommits()),
                Update.update(model, new Tick()).cmds());

        Model statusFailed = Update.update(Update.init("repo").model(), new LoadFailed(new LoadStatus(), "no git"))
                .model();
        assertEquals(new Failed<>("no git"), statusFailed.status());
        assertEquals(List.of(new LoadStatus()), Update.update(statusFailed, new Tick()).cmds());
    }

    @Test
    void checkoutRequestOfAnotherBranchChecksItOut() {
        Model failedBefore = Update.update(loaded(), new CheckoutFailed("feature", "boom")).model();

        Next next = Update.update(failedBefore, new CheckoutRequested("feature"));

        assertEquals(List.of(new Checkout("feature")), next.cmds());
        assertEquals(Optional.empty(), next.model().error());
    }

    @Test
    void checkoutRequestIsIgnoredForTheCurrentOrAnUnknownBranch() {
        Model model = loaded();

        assertEquals(List.of(), Update.update(model, new CheckoutRequested("main")).cmds());
        assertEquals(List.of(), Update.update(model, new CheckoutRequested("gone")).cmds());
        assertEquals(List.of(), Update.update(Update.init("repo").model(), new CheckoutRequested("feature")).cmds());
    }

    @Test
    void checkedOutReloadsStatusAndBranches() {
        Next next = Update.update(loaded(), new CheckedOut("feature"));

        assertEquals(List.of(new LoadStatus(), new LoadBranches()), next.cmds());
        assertEquals(Optional.empty(), next.model().error());
    }

    @Test
    void failedCheckoutIsStoredWithoutFurtherCommands() {
        Next next = Update.update(loaded(), new CheckoutFailed("feature", "local changes would be overwritten"));

        assertEquals(Optional.of("Checkout of feature failed: local changes would be overwritten"),
                next.model().error());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void toggleStagesTheFilesWithUnstagedChanges() {
        FileEntry unstaged = new FileEntry("a.txt", ChangeType.MODIFIED);
        FileEntry untracked = new FileEntry("b.txt", ChangeType.UNTRACKED);
        FileEntry staged = new FileEntry("c.txt", ChangeType.MODIFIED, true, false);

        Next next = Update.update(loaded(), new ToggleStageRequested(List.of(unstaged, untracked, staged)));

        assertEquals(List.of(new Stage(List.of("a.txt", "b.txt"))), next.cmds());
    }

    @Test
    void toggleStagesAPartiallyStagedFile() {
        FileEntry both = new FileEntry("a.txt", ChangeType.MODIFIED, true, true);

        assertEquals(List.of(new Stage(List.of("a.txt"))),
                Update.update(loaded(), new ToggleStageRequested(List.of(both))).cmds());
    }

    @Test
    void toggleUnstagesAddedFilesAndResetsTheOthers() {
        FileEntry added = new FileEntry("a.txt", ChangeType.ADDED);
        FileEntry modified = new FileEntry("b.txt", ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry("c.txt", ChangeType.DELETED, true, false);

        Next next = Update.update(loaded(), new ToggleStageRequested(List.of(added, modified, deleted)));

        assertEquals(List.of(new Unstage(List.of("a.txt"), List.of("b.txt", "c.txt"))), next.cmds());
    }

    @Test
    void toggleOfNothingDoesNothing() {
        assertEquals(List.of(), Update.update(loaded(), new ToggleStageRequested(List.of())).cmds());
    }

    @Test
    void toggledReloadsAndFailureIsReported() {
        assertEquals(List.of(new LoadStatus(), new LoadBranches()),
                Update.update(loaded(), new StageToggled()).cmds());

        Next failed = Update.update(loaded(), new StageToggleFailed("fatal: pathspec"));
        assertEquals(Optional.of("Staging failed: fatal: pathspec"), failed.model().error());
        assertEquals(List.of(new LoadStatus(), new LoadBranches()), failed.cmds());
    }

    @Test
    void selectingABranchLoadsItsLogOnce() {
        Next next = Update.update(loaded(), new BranchSelected("feature"));

        assertEquals(Optional.of(new BranchLog("feature", new Loading<>())), next.model().branchLog());
        assertEquals(List.of(new LoadBranchLog("feature")), next.cmds());

        Next again = Update.update(next.model(), new BranchSelected("feature"));
        assertSame(next.model(), again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void switchingBranchesKeepsTheLastLoadedLogUntilTheNewOneArrives() {
        Model feature = Update.update(
                Update.update(loaded(), new BranchSelected("feature")).model(),
                new BranchLogLoaded("feature", FEATURE_COMMITS)).model();
        BranchLog featureLog = feature.branchLog().orElseThrow();

        Model main = Update.update(feature, new BranchSelected("main")).model();
        assertEquals(Optional.of(new BranchLog("main", new Loading<>(), Optional.of(featureLog))), main.branchLog());

        Model other = Update.update(main, new BranchSelected("other")).model();
        assertEquals(Optional.of(featureLog), other.branchLog().orElseThrow().previous());

        Model done = Update.update(other, new BranchLogLoaded("other", FEATURE_COMMITS)).model();
        assertEquals(Optional.empty(), done.branchLog().orElseThrow().previous());
    }

    @Test
    void loadedLogIsShownForTheSelectedBranch() {
        Model model = Update.update(loaded(), new BranchSelected("feature")).model();

        Model next = Update.update(model, new BranchLogLoaded("feature", FEATURE_COMMITS)).model();

        assertEquals(Optional.of(new BranchLog("feature", new Loaded<>(FEATURE_COMMITS))), next.branchLog());
    }

    @Test
    void resultsForABranchThatIsNoLongerSelectedAreDropped() {
        Model model = Update.update(loaded(), new BranchSelected("feature")).model();
        model = Update.update(model, new BranchSelected("main")).model();

        assertEquals(model.branchLog(), Update.update(model, new BranchLogLoaded("feature", FEATURE_COMMITS))
                .model().branchLog());
        assertEquals(model.branchLog(), Update.update(model, new LoadFailed(new LoadBranchLog("feature"), "boom"))
                .model().branchLog());
    }

    @Test
    void failedLogIsRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new BranchSelected("feature")).model();
        model = Update.update(model, new LoadFailed(new LoadBranchLog("feature"), "boom")).model();

        assertEquals(Optional.of(new BranchLog("feature", new Failed<>("boom"))), model.branchLog());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new LoadBranchLog("feature")),
                Update.update(model, new Tick()).cmds());
    }

    @Test
    void movedTipOfTheSelectedBranchReloadsItsLog() {
        Model model = Update.update(loaded(), new BranchSelected("feature")).model();
        model = Update.update(model, new BranchLogLoaded("feature", FEATURE_COMMITS)).model();

        assertEquals(List.of(), Update.update(model, new BranchesLoaded(BRANCHES)).cmds());
        assertEquals(List.of(), Update.update(model, new BranchesLoaded(
                List.of(new Branch("main", true, "moved"), new Branch("feature", false, "ffff")))).cmds());
        assertEquals(List.of(new LoadBranchLog("feature")), Update.update(model, new BranchesLoaded(
                List.of(new Branch("main", true, "aaaa"), new Branch("feature", false, "moved")))).cmds());
    }

    @Test
    void highlightingFilesLoadsTheirDiffOnce() {
        Model model = loaded();
        List<FileEntry> files = DIRTY.files();

        Next next = Update.update(model, new Msg.FilesSelected(files));
        assertEquals(List.of(new Cmd.LoadFileDiff(files)), next.cmds());
        assertEquals(Optional.of(new FileDiff(files, new Loading<>())), next.model().fileDiff());

        assertEquals(List.of(), Update.update(next.model(), new Msg.FilesSelected(files)).cmds());
    }

    @Test
    void aDiffOfOtherFilesThanTheHighlightedOnesIsDropped() {
        Model model = Update.update(loaded(), new Msg.FilesSelected(DIRTY.files())).model();
        List<FileEntry> other = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));

        assertEquals(model.fileDiff(), Update.update(model, new Msg.FileDiffLoaded(other, "x")).model().fileDiff());
        assertEquals(Optional.of(new FileDiff(DIRTY.files(), new Loaded<>("+a"))),
                Update.update(model, new Msg.FileDiffLoaded(DIRTY.files(), "+a")).model().fileDiff());
    }

    @Test
    void ticksReloadTheDiffAndKeepTheOldOneVisibleWhileLoading() {
        Model model = Update.update(loaded(), new Msg.FilesSelected(DIRTY.files())).model();
        model = Update.update(model, new Msg.FileDiffLoaded(DIRTY.files(), "+a")).model();

        Next tick = Update.update(model, new Tick());
        assertEquals(true, tick.cmds().contains(new Cmd.LoadFileDiff(DIRTY.files())));

        List<FileEntry> next = List.of(new FileEntry("b.txt", ChangeType.UNTRACKED));
        FileDiff loading = Update.update(model, new Msg.FilesSelected(next)).model().fileDiff().orElseThrow();
        assertEquals(Optional.of(new FileDiff(DIRTY.files(), new Loaded<>("+a"))), loading.previous());
    }

    @Test
    void aFailedDiffLoadIsShown() {
        Model model = Update.update(loaded(), new Msg.FilesSelected(DIRTY.files())).model();
        Cmd.LoadFileDiff cmd = new Cmd.LoadFileDiff(DIRTY.files());

        assertEquals(Optional.of(new FileDiff(DIRTY.files(), new Failed<>("boom"))),
                Update.update(model, new LoadFailed(cmd, "boom")).model().fileDiff());
    }

    /**
     * A model with everything loaded and nothing running.
     */
    private static Model loaded() {
        Model model = Update.init("repo").model();
        model = Update.update(model, new StatusLoaded(CLEAN)).model();
        model = Update.update(model, new BranchesLoaded(BRANCHES)).model();
        return Update.update(model, new CommitsLoaded(COMMITS)).model();
    }
}
