package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Cmd.BranchCmd.Checkout;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadStatus;
import net.vanfleteren.jazygit.state.Cmd.Stage;
import net.vanfleteren.jazygit.state.Cmd.Unstage;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Msg.Tick;
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
        Next next = Update.update(Update.init("repo").model(), new LoadMsg.StatusLoaded(CLEAN));

        assertEquals(new Loaded<>(CLEAN), next.model().status());
        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void tickDoesNotRepeatLoadsThatAreStillRunning() {
        Model model = Update.init("repo").model();

        assertEquals(List.of(), Update.update(model, new Tick()).cmds());

        Model statusDone = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        assertEquals(List.of(new LoadStatus()), Update.update(statusDone, new Tick()).cmds());
    }

    @Test
    void unchangedStatusKeepsTheSameInstanceAndDoesNotReloadCommits() {
        Model model = loaded();

        Next next = Update.update(model, new LoadMsg.StatusLoaded(new RepoStatus("main", "aaaa", List.of())));

        assertSame(model.status(), next.model().status());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void workingTreeChangeReplacesStatusButNotCommits() {
        Model model = loaded();

        Next next = Update.update(model, new LoadMsg.StatusLoaded(DIRTY));

        assertEquals(new Loaded<>(DIRTY), next.model().status());
        assertSame(model.commits(), next.model().commits());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void movedHeadReloadsCommits() {
        Next next = Update.update(loaded(), new LoadMsg.StatusLoaded(MOVED));

        assertEquals(List.of(new LoadCommits()), next.cmds());
    }

    @Test
    void unchangedBranchesAndCommitsKeepTheirInstances() {
        Model model = loaded();

        assertSame(model.branches(),
                Update.update(model, new LoadMsg.BranchesLoaded(List.copyOf(BRANCHES))).model().branches());
        assertSame(model.commits(),
                Update.update(model, new LoadMsg.CommitsLoaded(List.copyOf(COMMITS))).model().commits());
    }

    @Test
    void failuresAreStoredAndRetriedOnTheNextTick() {
        Model model = Update.update(loaded(), new LoadMsg.Failed(new LoadCommits(), "boom")).model();
        assertEquals(new Failed<>("boom"), model.commits());
        assertEquals(List.of(new LoadStatus(), new LoadBranches(), new LoadCommits()),
                Update.update(model, new Tick()).cmds());

        Model statusFailed = Update.update(Update.init("repo").model(), new LoadMsg.Failed(new LoadStatus(), "no git"))
                .model();
        assertEquals(new Failed<>("no git"), statusFailed.status());
        assertEquals(List.of(new LoadStatus()), Update.update(statusFailed, new Tick()).cmds());
    }

    @Test
    void checkoutRequestOfAnotherBranchChecksItOut() {
        Model failedBefore = Update.update(loaded(), new CheckoutMsg.Failed("feature", "boom")).model();

        Next next = Update.update(failedBefore, new CheckoutMsg.Requested("feature"));

        assertEquals(List.of(new Checkout("feature")), next.cmds());
        assertEquals(Optional.empty(), next.model().error());
    }

    @Test
    void checkoutRequestIsIgnoredForTheCurrentOrAnUnknownBranch() {
        Model model = loaded();

        assertEquals(List.of(), Update.update(model, new CheckoutMsg.Requested("main")).cmds());
        assertEquals(List.of(), Update.update(model, new CheckoutMsg.Requested("gone")).cmds());
        assertEquals(List.of(), Update.update(Update.init("repo").model(), new CheckoutMsg.Requested("feature")).cmds());
    }

    @Test
    void newBranchRequestOpensTheDialogAndCancelClosesIt() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested("feature")).model();
        assertEquals(Optional.of("feature"), open.newBranchBase());

        Next cancelled = Update.update(open, new NewBranchMsg.Cancelled());
        assertEquals(Optional.empty(), cancelled.model().newBranchBase());
        assertEquals(List.of(), cancelled.cmds());
    }

    @Test
    void confirmedNewBranchIsCreatedAtTheBase() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new NewBranchMsg.Confirmed(" topic "));

        assertEquals(List.of(new Cmd.BranchCmd.CreateBranch("topic", "feature")), next.cmds());
        assertEquals(Optional.empty(), next.model().newBranchBase());
    }

    @Test
    void blankNewBranchNameKeepsTheDialogOpen() {
        Model open = Update.update(loaded(), new NewBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new NewBranchMsg.Confirmed("  "));

        assertEquals(List.of(), next.cmds());
        assertEquals(Optional.of("feature"), next.model().newBranchBase());
    }

    @Test
    void failedBranchCreationIsStored() {
        Model model = Update.update(loaded(), new NewBranchMsg.Failed("topic", "boom")).model();

        assertEquals(Optional.of("Creating branch topic failed: boom"), model.error());
    }

    @Test
    void deleteRequestOpensThePopupOnlyForAnotherKnownBranch() {
        Model model = loaded();

        assertEquals(Optional.of("feature"),
                Update.update(model, new DeleteBranchMsg.Requested("feature")).model().deleteTarget());
        assertEquals(Optional.empty(), Update.update(model, new DeleteBranchMsg.Requested("main")).model().deleteTarget());
        assertEquals(Optional.empty(), Update.update(model, new DeleteBranchMsg.Requested("gone")).model().deleteTarget());
    }

    @Test
    void remoteScopesAreRefusedForALocalOnlyBranch() {
        Model open = Update.update(loaded(), new DeleteBranchMsg.Requested("feature")).model();

        for (Cmd.BranchCmd.DeleteBranch.DeleteScope scope : List.of(Cmd.BranchCmd.DeleteBranch.DeleteScope.REMOTE, Cmd.BranchCmd.DeleteBranch.DeleteScope.BOTH)) {
            Next next = Update.update(open, new DeleteBranchMsg.Chosen(scope));
            assertEquals(List.of(), next.cmds());
            assertEquals(Optional.of("feature"), next.model().deleteTarget());
        }
        assertEquals(List.of(new Cmd.BranchCmd.DeleteBranch("feature", Cmd.BranchCmd.DeleteBranch.DeleteScope.LOCAL)),
                Update.update(open, new DeleteBranchMsg.Chosen(Cmd.BranchCmd.DeleteBranch.DeleteScope.LOCAL)).cmds());
    }

    @Test
    void remoteScopesAreRefusedForTheDefaultBranch() {
        Model model = Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(new Branch("main", true, "aaaa"),
                new Branch("trunk", false, "tttt", java.time.Instant.EPOCH, true, true)))).model();
        Model open = Update.update(model, new DeleteBranchMsg.Requested("trunk")).model();

        assertEquals(List.of(), Update.update(open, new DeleteBranchMsg.Chosen(Cmd.BranchCmd.DeleteBranch.DeleteScope.REMOTE)).cmds());
        assertEquals(List.of(), Update.update(open, new DeleteBranchMsg.Chosen(Cmd.BranchCmd.DeleteBranch.DeleteScope.BOTH)).cmds());
        assertEquals(List.of(new Cmd.BranchCmd.DeleteBranch("trunk", Cmd.BranchCmd.DeleteBranch.DeleteScope.LOCAL)),
                Update.update(open, new DeleteBranchMsg.Chosen(Cmd.BranchCmd.DeleteBranch.DeleteScope.LOCAL)).cmds());
    }

    @Test
    void choosingAScopeDeletesTheBranchAndClosesThePopup() {
        Model withRemote = Update.update(loaded(), new LoadMsg.BranchesLoaded(
                List.of(new Branch("main", true, "aaaa"), new Branch("feature", false, "ffff", java.time.Instant.EPOCH, true)))).model();
        Model open = Update.update(withRemote, new DeleteBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new DeleteBranchMsg.Chosen(Cmd.BranchCmd.DeleteBranch.DeleteScope.REMOTE));

        assertEquals(List.of(new Cmd.BranchCmd.DeleteBranch("feature", Cmd.BranchCmd.DeleteBranch.DeleteScope.REMOTE)), next.cmds());
        assertEquals(Optional.empty(), next.model().deleteTarget());
    }

    @Test
    void cancellingClosesThePopupWithoutDeleting() {
        Model open = Update.update(loaded(), new DeleteBranchMsg.Requested("feature")).model();

        Next next = Update.update(open, new DeleteBranchMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Optional.empty(), next.model().deleteTarget());
    }

    @Test
    void failedDeletionIsStored() {
        Model model = Update.update(loaded(), new DeleteBranchMsg.Failed("feature", "boom")).model();

        assertEquals(Optional.of("Deleting branch feature failed: boom"), model.error());
    }

    @Test
    void checkedOutReloadsStatusAndBranches() {
        Next next = Update.update(loaded(), new CheckoutMsg.Done("feature"));

        assertEquals(List.of(new LoadStatus(), new LoadBranches()), next.cmds());
        assertEquals(Optional.empty(), next.model().error());
    }

    @Test
    void failedCheckoutIsStoredWithoutFurtherCommands() {
        Next next = Update.update(loaded(), new CheckoutMsg.Failed("feature", "local changes would be overwritten"));

        assertEquals(Optional.of("Checkout of feature failed: local changes would be overwritten"),
                next.model().error());
        assertEquals(List.of(), next.cmds());
    }

    private Model withFiles(FileEntry... files) {
        return loaded().withStatus(new Loaded<>(new RepoStatus("main", "aaaa", List.of(files))));
    }

    private Model withCommits(Commit... commits) {
        return loaded().withCommits(new Loaded<>(List.of(commits)));
    }

    private static Commit commit(String message, String body) {
        return new Commit("abc1234", "me", "me@example.com", Instant.EPOCH, message, body);
    }

    @Test
    void rewordOpensTheDialogForTheLastCommit() {
        Commit last = commit("last", "details");
        Model model = Update.update(withCommits(last, commit("older", "")), new RewordMsg.Requested(0)).model();

        assertEquals(Optional.of(last), model.rewording());
    }

    @Test
    void onlyTheLastCommitCanBeReworded() {
        Model model = Update.update(withCommits(commit("last", ""), commit("older", "")), new RewordMsg.Requested(1))
                .model();

        assertEquals(Optional.empty(), model.rewording());
        assertEquals(Optional.of("Only the last commit can be reworded"), model.error());
    }

    @Test
    void confirmingTheRewordRewordsAndClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(0)).model();

        Next next = Update.update(open, new RewordMsg.Confirmed(" better ", "\nwhy\n"));

        assertEquals(List.of(new Cmd.Reword("better", "why")), next.cmds());
        assertEquals(Optional.empty(), next.model().rewording());
    }

    @Test
    void blankSummaryKeepsTheRewordDialogOpen() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(0)).model();

        Next next = Update.update(open, new RewordMsg.Confirmed(" ", "why"));

        assertEquals(List.of(), next.cmds());
        assertEquals(open.rewording(), next.model().rewording());
    }

    @Test
    void cancellingTheRewordClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(0)).model();

        assertEquals(Optional.empty(), Update.update(open, new RewordMsg.Cancelled()).model().rewording());
    }

    @Test
    void amendAsksForConfirmationThenAmendsWithTheStagedFiles() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED),
                new FileEntry("b.txt", ChangeType.MODIFIED)), new AmendMsg.Requested()).model();
        assertEquals(true, asking.amendPrompt());

        Next next = Update.update(asking, new AmendMsg.Confirmed());

        assertEquals(List.of(new Cmd.Amend(List.of())), next.cmds());
        assertEquals(false, next.model().amendPrompt());
    }

    @Test
    void amendStagesAllFilesWhenNoneIsStaged() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED),
                new FileEntry("b.txt", ChangeType.UNTRACKED)), new AmendMsg.Requested()).model();

        assertEquals(List.of(new Cmd.Amend(List.of("a.txt", "b.txt"))),
                Update.update(asking, new AmendMsg.Confirmed()).cmds());
    }

    @Test
    void cancellingTheAmendClosesThePrompt() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new AmendMsg.Requested())
                .model();

        Next next = Update.update(asking, new AmendMsg.Cancelled());

        assertEquals(false, next.model().amendPrompt());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void amendWithoutChangesIsAnError() {
        assertEquals(Optional.of("Nothing to amend the last commit with"),
                Update.update(withFiles(), new AmendMsg.Requested()).model().error());
    }

    @Test
    void commitOpensTheDialogWhenSomethingIsStaged() {
        Model model = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertEquals(true, model.commitOpen());
        assertEquals(false, model.stageAllPrompt());
    }

    @Test
    void commitAsksToStageAllWhenNothingIsStaged() {
        Model model = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED)), new CommitMsg.Requested())
                .model();

        assertEquals(true, model.stageAllPrompt());
        assertEquals(false, model.commitOpen());
    }

    @Test
    void commitWithoutChangesIsAnError() {
        Model model = Update.update(withFiles(), new CommitMsg.Requested()).model();

        assertEquals(Optional.of("Nothing to commit"), model.error());
        assertEquals(false, model.stageAllPrompt());
    }

    @Test
    void confirmingStageAllStagesEverythingUnstaged() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED),
                new FileEntry("b.txt", ChangeType.UNTRACKED)), new CommitMsg.Requested()).model();

        Next next = Update.update(asking, new CommitMsg.StageAllConfirmed());

        assertEquals(List.of(new Cmd.StageForCommit(List.of("a.txt", "b.txt"))), next.cmds());
        assertEquals(false, next.model().stageAllPrompt());
        assertEquals(true, Update.update(next.model(), new CommitMsg.StagedForCommit()).model().commitOpen());
    }

    @Test
    void cancellingStageAllClosesThePrompt() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED)),
                new CommitMsg.Requested()).model();

        assertEquals(false, Update.update(asking, new CommitMsg.StageAllCancelled()).model().stageAllPrompt());
    }

    @Test
    void confirmingTheCommitCommitsAndClosesTheDialog() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(open, new CommitMsg.Confirmed(" summary ", "\ndetails\n"));

        assertEquals(List.of(new Cmd.Commit("summary", "details")), next.cmds());
        assertEquals(false, next.model().commitOpen());
    }

    @Test
    void blankSummaryKeepsTheCommitDialogOpen() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(open, new CommitMsg.Confirmed("  ", "details"));

        assertEquals(List.of(), next.cmds());
        assertEquals(true, next.model().commitOpen());
    }

    @Test
    void cancellingTheCommitClosesTheDialog() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertEquals(false, Update.update(open, new CommitMsg.Cancelled()).model().commitOpen());
    }

    @Test
    void toggleStagesTheFilesWithUnstagedChanges() {
        FileEntry unstaged = new FileEntry("a.txt", ChangeType.MODIFIED);
        FileEntry untracked = new FileEntry("b.txt", ChangeType.UNTRACKED);
        FileEntry staged = new FileEntry("c.txt", ChangeType.MODIFIED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(unstaged, untracked, staged)));

        assertEquals(List.of(new Stage(List.of("a.txt", "b.txt"))), next.cmds());
    }

    @Test
    void toggleStagesAPartiallyStagedFile() {
        FileEntry both = new FileEntry("a.txt", ChangeType.MODIFIED, true, true);

        assertEquals(List.of(new Stage(List.of("a.txt"))),
                Update.update(loaded(), new StageMsg.Requested(List.of(both))).cmds());
    }

    @Test
    void toggleUnstagesAddedFilesAndResetsTheOthers() {
        FileEntry added = new FileEntry("a.txt", ChangeType.ADDED);
        FileEntry modified = new FileEntry("b.txt", ChangeType.MODIFIED, true, false);
        FileEntry deleted = new FileEntry("c.txt", ChangeType.DELETED, true, false);

        Next next = Update.update(loaded(), new StageMsg.Requested(List.of(added, modified, deleted)));

        assertEquals(List.of(new Unstage(List.of("a.txt"), List.of("b.txt", "c.txt"))), next.cmds());
    }

    @Test
    void toggleOfNothingDoesNothing() {
        assertEquals(List.of(), Update.update(loaded(), new StageMsg.Requested(List.of())).cmds());
    }

    @Test
    void toggledReloadsAndFailureIsReported() {
        assertEquals(List.of(new LoadStatus(), new LoadBranches()),
                Update.update(loaded(), new StageMsg.Done()).cmds());

        Next failed = Update.update(loaded(), new StageMsg.Failed("fatal: pathspec"));
        assertEquals(Optional.of("Staging failed: fatal: pathspec"), failed.model().error());
        assertEquals(List.of(new LoadStatus(), new LoadBranches()), failed.cmds());
    }

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

    /**
     * A model with everything loaded and nothing running.
     */
    private static Model loaded() {
        Model model = Update.init("repo").model();
        model = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        model = Update.update(model, new LoadMsg.BranchesLoaded(BRANCHES)).model();
        return Update.update(model, new LoadMsg.CommitsLoaded(COMMITS)).model();
    }
}
