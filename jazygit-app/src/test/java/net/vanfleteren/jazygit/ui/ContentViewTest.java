package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.help.HelpPopup;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.prs.PrOverview;
import net.vanfleteren.jazygit.ui.ContentView.Body;
import net.vanfleteren.jazygit.ui.ContentView.Shown;
import net.vanfleteren.jazygit.ui.ContentView.Tone;
import org.junit.jupiter.api.Test;

/**
 * Tests on {@link ContentView}: what the content panel shows in a model, without drawing it.
 */
class ContentViewTest {

    private static final DiffLook PLAIN_LOOK = new DiffLook(DiffStyle.PLAIN, true, true);
    private static final DiffLook RICH_LOOK = new DiffLook(DiffStyle.RICH, true, true);

    private static final PullRequestSummary PULL_REQUEST = new PullRequestSummary(
            "owner/repo",
            42,
            "Fix the login",
            "alice",
            "fix/login",
            false,
            false,
            PullRequest.Checks.NONE,
            PullRequest.Review.NONE,
            Instant.EPOCH,
            "https://github.com/owner/repo/pull/42");

    private static final PullRequestPreview READ = new PullRequestPreview("main", "Why", "+fixed\n", false);

    private final Model model = TestModels.loaded(new SampleData(), "feature/initial-layout");

    private static Shown dim(String title, String text) {
        return new Shown.Message(title, text, Tone.DIM);
    }

    private static Shown loading(String title) {
        return dim(title, Placeholders.loading());
    }

    /**
     * The pull requests screen, with {@code preview} of the highlighted pull request, once one is highlighted.
     */
    private static Model pullRequests(Option<Preview<PullRequestSummary, PullRequestPreview>> preview) {
        return TestModels.loaded()
                .withScreen(Screen.PULL_REQUESTS)
                .withPrOverview(PrOverview.INITIAL
                        .withHighlighted(preview.map(Preview::key))
                        .withPreview(preview));
    }

    /**
     * {@code diff} shown as it is, whole.
     */
    private static ContentView.ShownDiff whole(String diff) {
        return new ContentView.ShownDiff(DiffText.of(diff), Option.none());
    }

    /**
     * The one area {@code shown} is.
     */
    private static Shown.Area area(Shown shown) {
        return assertInstanceOf(Shown.Area.class, shown);
    }

    private static List<String> shortShas(Shown.Area log) {
        return assertInstanceOf(Body.BranchLog.class, log.body()).commits().map(Commit::shortSha);
    }

    private static Commit plain(String sha) {
        return Fixtures.commit(new Sha(sha), "Ada", "a@e.com", Instant.EPOCH, sha, "", List.empty());
    }

    /**
     * The model with commit aaaa highlighted and its detail, {@code changes}, loaded while diffing with
     * {@code options}.
     */
    private static Model showingCommit(DiffOptions options, String changes) {
        return Update.update(
                        Update.update(
                                        TestModels.loaded()
                                                .mapPreferences(preferences ->
                                                        preferences.withIgnoreWhitespace(options.ignoreWhitespace())),
                                        new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), options, DiffText.of(changes)))
                .model();
    }

    /**
     * The model diverged from upstream by commit cccc, which is highlighted.
     */
    private static Model divergenceHighlighted() {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView -> repoView.withDivergence(Option.of(new DivergenceView(
                        new BranchName("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))),
                        DivergenceView.Against.UPSTREAM,
                        new Loadable.Loaded<>(new Divergence(
                                new Divergence.Side(List.of(plain("cccc")), 1), new Divergence.Side(List.of(), 0))),
                        Option.none()))));
        return Update.update(diverged, new SelectionMsg.DivergenceCommitSelected(new Sha("cccc")))
                .model();
    }

    /**
     * The model with the commits of the branch feature open, whose log is {@code commits}.
     */
    private static Model branchCommitsOpen(List<Commit> commits) {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        Model logRead = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.BranchSelected(feature))
                                .model(),
                        TestModels.branchLogLoaded(feature, commits))
                .model();
        return Update.update(logRead, new BranchCommitsMsg.Opened(feature)).model();
    }

    @Test
    void withoutChangedFilesTheFilesSaySoInsteadOfLoading() {
        assertEquals(dim("Diff", "No changed files"), ContentView.of(TestModels.loaded(), FilesPanel.ID));
    }

    @Test
    void eachPaneShowsLoadingWhileTheRepositoryLoads() {
        Model loading = TestModels.loading("repo");

        assertEquals(loading("Diff"), ContentView.of(loading, FilesPanel.ID));
        assertEquals(loading("Log"), ContentView.of(loading, BranchesPanel.ID));
        assertEquals(loading("Commit"), ContentView.of(loading, CommitsPanel.ID));
        assertEquals(loading("Stash"), ContentView.of(loading, StashPanel.ID));
    }

    @Test
    void anUnknownFocusOrNoneYetShowsTheFiles() {
        Shown files = ContentView.of(model, FilesPanel.ID);

        assertEquals(files, ContentView.of(model, "unknown"));
        assertEquals(files, ContentView.of(model, null));
    }

    @Test
    void theBranchesShowTheLogOfTheSelectedBranch() {
        Shown.Area log = area(ContentView.of(model, BranchesPanel.ID));

        assertEquals(ContentArea.LOG, log.area());
        assertEquals("Log: feature/initial-layout", log.title());
        assertTrue(shortShas(log).contains("0a1b2c3"), log.toString());
        assertFalse(shortShas(log).contains("a1b2c3d"), log.toString());
    }

    @Test
    void theBranchesShowLoadingBeforeABranchIsSelected() {
        assertEquals(loading("Log"), ContentView.of(TestModels.loaded(new SampleData()), BranchesPanel.ID));
    }

    @Test
    void theBranchesKeepShowingThePreviousLogWhileTheNewOneLoads() {
        Shown.Area log = area(ContentView.of(TestModels.switchingBranch(model, "other"), BranchesPanel.ID));

        assertEquals("Log: feature/initial-layout", log.title());
        assertTrue(shortShas(log).contains("0a1b2c3"), log.toString());
    }

    @Test
    void aPopupThatJustClosedKeepsShowingTheBranchLogUntilTheFocusIsBack() {
        // The popup is gone from the model, but has the focus until the render gives it back.
        Model closed = model.mapRepoView(repoView -> repoView.withFocusedPane(Option.of(BranchesPanel.ID)));

        assertEquals(
                "Log: feature/initial-layout",
                area(ContentView.of(closed, "new-branch")).title());
    }

    @Test
    void aPopupOpenedFromTheBranchesKeepsShowingTheBranchLog() {
        Model withPopup = model.mapRepoView(repoView -> repoView.withFocusedPane(Option.of(BranchesPanel.ID)))
                .openPopup(new HelpPopup(BranchesPanel.ID, List.empty()));

        assertEquals(
                "Log: feature/initial-layout",
                area(ContentView.of(withPopup, "help")).title());
    }

    @Test
    void aPopupOpenedFromTheCommitChangesKeepsShowingTheCommit() {
        // The area had the focus when the popup opened, so that is what the focus goes back to.
        Model withPopup = showingCommit(DiffOptions.DEFAULT, "")
                .mapRepoView(repoView -> repoView.withFocusedPane(Option.of(ContentArea.COMMIT.id())))
                .openPopup(new HelpPopup(ContentArea.COMMIT.id(), List.empty()));

        assertEquals("Commit: aaaa", area(ContentView.of(withPopup, "help")).title());
    }

    @Test
    void theCommitsShowTheHighlightedCommitAndWhatItChanged() {
        Model selected = showingCommit(DiffOptions.DEFAULT, "+added line\n");
        Commit commit = selected.commit(new Sha("aaaa")).get();

        assertEquals(
                new Shown.Area(
                        ContentArea.COMMIT,
                        "Commit: aaaa",
                        new ScrolledContent.CommitChanges(commit, DiffText.of("+added line\n")),
                        new Body.CommitChanges(commit, whole("+added line\n"), PLAIN_LOOK)),
                ContentView.of(selected, CommitsPanel.ID));
    }

    @Test
    void theFilesOfACommitShowWhatItChangedInTheHighlightedNodeInTheStyleChosen() {
        CommitFilesView.Changes of = new CommitFilesView.Changes.OfCommit(new Sha("aaaa"));
        CommitFile file = new CommitFile("src/a.txt", ChangeType.MODIFIED);
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model open = Update.update(
                        Update.update(rich, new CommitFilesMsg.Opened(of, CommitsPanel.ID))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                .model();
        Model selected = Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), rich.preferences().diffOptions(), DiffText.of("+a\n")))
                .model();

        assertEquals(
                new Shown.Area(
                        ContentArea.COMMIT_FILE,
                        "aaaa · src/a.txt",
                        new ScrolledContent.FileChanges(of, List.of(file), DiffText.of("+a\n")),
                        new Body.Diff(whole("+a\n"), RICH_LOOK)),
                ContentView.of(selected, FilesPanel.ID));
        assertEquals(
                new Shown.Message("Files of aaaa", Placeholders.loading(), Tone.DIM),
                ContentView.of(open, FilesPanel.ID));
    }

    @Test
    void whenOnlyTheFilesOfAPullRequestWithRemarksAreListedAndNoneIsTheDiffShownBeforeIsNot() {
        CommitFilesView.Changes of = new CommitFilesView.Changes.OfPullRequest(
                new PullRequestCommits(7, "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
        CommitFile file = new CommitFile("src/a.txt", ChangeType.MODIFIED);
        Model open = Update.update(
                        Update.update(TestModels.loaded(), new CommitFilesMsg.Opened(of, BranchesPanel.ID))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                .model();
        Model selected = Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), open.preferences().diffOptions(), DiffText.of("+a\n")))
                .model();
        Model unresolved =
                Update.update(selected, new CommitFilesMsg.ListedCycled()).model();
        assertInstanceOf(Shown.Area.class, ContentView.of(selected, FilesPanel.ID));

        // While the remarks are read.
        assertEquals(loading("Files of #7"), ContentView.of(unresolved, FilesPanel.ID));

        Model none = Update.update(
                        unresolved,
                        new CommitFilesMsg.RemarksLoaded(
                                7,
                                new ReviewRemarks(
                                        PullRequestDetails.State.OPEN, Option.none(), Option.none(), List.empty()),
                                HashSet.empty(),
                                0))
                .model();
        assertEquals(dim("Files of #7", "No file has unresolved remarks"), ContentView.of(none, FilesPanel.ID));
        assertEquals(
                dim("Files of #7", "No file has remarks"),
                ContentView.of(
                        Update.update(none, new CommitFilesMsg.ListedCycled()).model(), FilesPanel.ID));

        Failure failure = new Failure.Bug(new Exception("no network"));
        assertEquals(
                new Shown.Message("Files of #7", Placeholders.error(failure), Tone.ERROR),
                ContentView.of(
                        Update.update(unresolved, new CommitFilesMsg.RemarksFailed(7, failure, HashSet.empty(), 0))
                                .model(),
                        FilesPanel.ID));
    }

    @Test
    void ignoringWhitespaceTheFilesSaySoAndThatOnlyWhitespaceChanged() {
        DiffOptions ignoring = DiffOptions.DEFAULT.withIgnoreWhitespace(true);
        Model dirty = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(TestModels.DIRTY))
                .model()
                .mapPreferences(preferences -> preferences.withIgnoreWhitespace(ignoring.ignoreWhitespace()));
        List<FileEntry> files = TestModels.DIRTY.files();
        Model selected =
                Update.update(dirty, new SelectionMsg.FilesSelected(files)).model();
        Model blank = Update.update(selected, new LoadMsg.FileDiffLoaded(files, ignoring, new Diffs("", "")))
                .model();
        Model changed = Update.update(selected, new LoadMsg.FileDiffLoaded(files, ignoring, new Diffs("", "+a\n")))
                .model();

        assertEquals(
                dim("Diff · ignoring whitespace", "No changes besides whitespace"),
                ContentView.of(blank, FilesPanel.ID));
        assertEquals(
                List.of("Unstaged changes · ignoring whitespace"),
                ContentView.of(changed, FilesPanel.ID).areas().map(Shown.Area::title));
    }

    @Test
    void theFilesShowTheStagedChangesAboveTheUnstagedOnes() {
        Model selected = TestModels.withFilesSelected(
                new SampleData(),
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        Shown.Stacked diffs = assertInstanceOf(Shown.Stacked.class, ContentView.of(selected, FilesPanel.ID));
        assertEquals(
                List.of(ContentArea.STAGED, ContentArea.UNSTAGED), diffs.areas().map(Shown.Area::area));
        assertEquals(
                List.of("Staged changes", "Unstaged changes"), diffs.areas().map(Shown.Area::title));
    }

    @Test
    void theStagedAndUnstagedChangesAreDrawnInTheStyleChosen() {
        Model selected = TestModels.withFilesSelected(
                new SampleData(),
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));
        Model rich = selected.mapPreferences(
                preferences -> preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));

        assertEquals(
                List.of(PLAIN_LOOK, PLAIN_LOOK),
                ContentView.of(selected, FilesPanel.ID)
                        .areas()
                        .map(area ->
                                assertInstanceOf(Body.Diff.class, area.body()).look()));
        assertEquals(
                List.of(RICH_LOOK, RICH_LOOK),
                ContentView.of(rich, FilesPanel.ID)
                        .areas()
                        .map(area ->
                                assertInstanceOf(Body.Diff.class, area.body()).look()));
    }

    @Test
    void aCommitThatOnlyChangedWhitespaceSaysSoWhileIgnoringIt() {
        Shown.Area commit = area(
                ContentView.of(showingCommit(DiffOptions.DEFAULT.withIgnoreWhitespace(true), ""), CommitsPanel.ID));

        assertEquals("Commit: aaaa · ignoring whitespace", commit.title());
        assertEquals(
                "No changes besides whitespace\n",
                assertInstanceOf(Body.CommitChanges.class, commit.body())
                        .changes()
                        .text());
    }

    @Test
    void withADivergenceOpenTheBranchesShowItsHighlightedCommit() {
        Model selected = Update.update(
                        divergenceHighlighted(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("cccc"), DiffOptions.DEFAULT, DiffText.of("+added line")))
                .model();

        assertEquals(
                new Shown.Area(
                        ContentArea.DIVERGENCE,
                        "Commit: cccc",
                        new ScrolledContent.CommitChanges(plain("cccc"), DiffText.of("+added line")),
                        new Body.CommitChanges(plain("cccc"), whole("+added line"), PLAIN_LOOK)),
                ContentView.of(selected, BranchesPanel.ID));
    }

    @Test
    void theDivergenceShowsLoadingWhileItsCommitIsReadAndOnlyOnTheLocalTab() {
        Model loading = divergenceHighlighted();

        assertEquals(loading("Commit"), ContentView.of(loading, BranchesPanel.ID));
        Model remoteTab = loading.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.REMOTE));
        // The remote branches show the log of the branch, which none is selected for here.
        assertEquals(loading("Log"), ContentView.of(remoteTab, BranchesPanel.ID));
    }

    @Test
    void aDivergenceWithoutCommitsSaysTheBranchesHaveNotDiverged() {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView -> repoView.withDivergence(Option.of(new DivergenceView(
                        new BranchName("main"),
                        new BranchRef.Local(new BranchName("feature")),
                        DivergenceView.Against.BASE,
                        new Loadable.Loaded<>(
                                new Divergence(new Divergence.Side(List.of(), 0), new Divergence.Side(List.of(), 0))),
                        Option.none()))));

        assertEquals(dim("Commit", "The branches have not diverged"), ContentView.of(diverged, BranchesPanel.ID));
    }

    @Test
    void withTheCommitsOfABranchOpenTheBranchesShowTheHighlightedOne() {
        Model loading = Update.update(
                        branchCommitsOpen(TestModels.FEATURE_COMMITS),
                        new SelectionMsg.BranchCommitSelected(new Sha("ffff")))
                .model();
        Model selected = Update.update(
                        loading,
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("ffff"), DiffOptions.DEFAULT, DiffText.of("+added line")))
                .model();

        assertEquals(loading("Commit"), ContentView.of(loading, BranchesPanel.ID));
        Shown.Area commit = area(ContentView.of(selected, BranchesPanel.ID));
        assertEquals(ContentArea.BRANCH_COMMIT, commit.area());
        assertEquals("Commit: ffff", commit.title());
        assertEquals(
                "+added line",
                assertInstanceOf(Body.CommitChanges.class, commit.body())
                        .changes()
                        .text());
    }

    @Test
    void withoutCommitsTheCommitsSaySoDimmed() {
        Model empty = TestModels.loaded().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.empty())));

        assertEquals(dim("Commit", "No commits"), ContentView.of(empty, CommitsPanel.ID));
    }

    @Test
    void aBranchWithoutCommitsSaysSoDimmed() {
        assertEquals(dim("Commit", "No commits"), ContentView.of(branchCommitsOpen(List.empty()), BranchesPanel.ID));
    }

    @Test
    void theStashesShowTheHighlightedStashAndWhatItStashed() {
        Stash stash =
                new Stash("stash@{0}", new Sha("5555"), "my work", Option.of(new BranchName("main")), Instant.EPOCH);
        Model stashed = Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of(stash)))
                .model();
        Model selected = Update.update(
                        Update.update(stashed, new SelectionMsg.StashSelected(stash.sha()))
                                .model(),
                        new LoadMsg.StashDetailLoaded(stash.sha(), DiffOptions.DEFAULT, DiffText.of("+added line\n")))
                .model();

        assertEquals(
                new Shown.Area(
                        ContentArea.STASH,
                        "Stash: stash@{0}",
                        new ScrolledContent.StashChanges(stash.sha(), DiffText.of("+added line\n")),
                        new Body.StashChanges(stash, whole("+added line\n"), PLAIN_LOOK)),
                ContentView.of(selected, StashPanel.ID));
    }

    @Test
    void withoutStashesTheStashesSaySo() {
        Model clean = Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of()))
                .model();

        assertEquals(dim("Stash", "No stash entries"), ContentView.of(clean, StashPanel.ID));
        assertEquals(List.empty(), ContentView.of(clean, StashPanel.ID).areas());
    }

    @Test
    void withoutAHighlightedPullRequestThePullRequestsScreenSaysSo() {
        Shown shown = ContentView.of(pullRequests(Option.none()), PullRequestsPanel.FIRST_PANE_ID);

        assertEquals(dim("Pull request", "No pull request highlighted"), shown);
        assertEquals(List.empty(), shown.areas());
    }

    @Test
    void aPullRequestShowsLoadingWhileItIsReadAndWhyItCouldNotBe() {
        Failure failure = TestModels.refused("no network");

        assertEquals(
                loading("Pull request"),
                ContentView.of(
                        pullRequests(Option.of(new Preview<>(PULL_REQUEST, Loadable.loading()))),
                        PullRequestsPanel.FIRST_PANE_ID));
        assertEquals(
                new Shown.Message("Pull request", Placeholders.error(failure), Tone.ERROR),
                ContentView.of(
                        pullRequests(Option.of(new Preview<>(PULL_REQUEST, new Loadable.Failed<>(failure)))),
                        PullRequestsPanel.FIRST_PANE_ID));
    }

    @Test
    void aReadPullRequestShowsWhatItSaysAndChanges() {
        Model read = pullRequests(Option.of(new Preview<>(PULL_REQUEST, new Loadable.Loaded<>(READ))));

        assertEquals(
                new Shown.Area(
                        ContentArea.PULL_REQUEST,
                        "Pull request: owner/repo#42",
                        new ScrolledContent.PullRequest(PULL_REQUEST, READ),
                        new Body.PullRequest(PULL_REQUEST, READ)),
                ContentView.of(read, PullRequestsPanel.FIRST_PANE_ID));
    }

    @Test
    void thePullRequestsScreenShowsThePullRequestWhicheverPaneHasTheFocus() {
        Model read = pullRequests(Option.of(new Preview<>(PULL_REQUEST, new Loadable.Loaded<>(READ))));

        assertEquals(ContentView.of(read, PullRequestsPanel.FIRST_PANE_ID), ContentView.of(read, FilesPanel.ID));
    }
}
