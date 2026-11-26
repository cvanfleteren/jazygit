package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.features.changes.panes.stash.StashPanel;
import net.vanfleteren.jazygit.features.chrome.help.HelpPopup;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.prs.content.PullRequestBody;
import net.vanfleteren.jazygit.features.github.prs.content.PullRequestSource;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.content.Tone;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * Tests on {@link ContentView}: what the content panel shows in a model, without drawing it, next to every pane
 * but the files, which {@code FilesContentViewTest} has.
 */
class ContentViewTest {

    private final Model model = TestModels.loaded(SampleData.git(), "feature/initial-layout");

    private static final PullRequestSummary PULL_REQUEST = new PullRequestSummary(
            new RepoSlug("owner/repo"),
            new PrNumber(42),
            "Fix the login",
            "alice",
            new ForgeBranch("fix/login"),
            false,
            false,
            PullRequest.Checks.NONE,
            PullRequest.Review.NONE,
            Instant.EPOCH,
            "https://github.com/owner/repo/pull/42");

    private static final PullRequestPreview READ =
            new PullRequestPreview(new ForgeBranch("main"), "Why", "+fixed\n", false);

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
                .withScreen(PrOverview.SCREEN)
                .withPart(
                        PrOverview.PART,
                        PrOverview.INITIAL
                                .withHighlighted(preview.map(Preview::key))
                                .withPreview(preview));
    }

    /**
     * The one area {@code shown} is.
     */
    private static Shown.Area area(Shown shown) {
        return assertInstanceOf(Shown.Area.class, shown);
    }

    private static List<String> shortShas(Shown.Area log) {
        return assertInstanceOf(BranchLogBody.class, log.body()).commits().map(Commit::shortSha);
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
                .mapRepoView(repoView ->
                        repoView.mapBranches(branches -> branches.withDivergence(Option.of(new DivergenceView(
                                new BranchName("main"),
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))),
                                DivergenceView.Against.UPSTREAM,
                                new Loadable.Loaded<>(new Divergence(
                                        new Divergence.Side(List.of(plain("cccc")), 1),
                                        new Divergence.Side(List.of(), 0))),
                                Option.none())))));
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
    void eachPaneShowsLoadingWhileTheRepositoryLoads() {
        Model loading = TestModels.loading("repo");

        assertEquals(
                ContentViewFixtures.note("Diff", Placeholders.loading()), ContentView.of(loading, FilesPanel.PANE));
        assertEquals(loading("Log"), ContentView.of(loading, Scopes.BRANCHES.pane()));
        assertEquals(loading("Commit"), ContentView.of(loading, CommitsPanel.PANE));
        assertEquals(loading("Stash"), ContentView.of(loading, StashPanel.PANE));
    }

    @Test
    void anUnknownFocusOrNoneYetShowsTheFiles() {
        Shown files = ContentView.of(model, FilesPanel.PANE);

        assertEquals(files, ContentView.of(model, new PaneId("unknown")));
        assertEquals(files, ContentView.of(model, Option.none()));
    }

    @Test
    void theBranchesShowTheLogOfTheSelectedBranch() {
        Shown.Area log = area(ContentView.of(model, Scopes.BRANCHES.pane()));

        assertEquals(BranchLogSource.AREA, log.area());
        assertEquals("Log: feature/initial-layout", log.title());
        assertTrue(shortShas(log).contains("0a1b2c3"), log.toString());
        assertFalse(shortShas(log).contains("a1b2c3d"), log.toString());
    }

    @Test
    void theBranchesShowLoadingBeforeABranchIsSelected() {
        assertEquals(loading("Log"), ContentView.of(TestModels.loaded(SampleData.git()), Scopes.BRANCHES.pane()));
    }

    @Test
    void aDetachedHeadHighlightedInTheBranchesSaysItsCommitsAreInTheCommitsPane() {
        Model detached = Update.update(
                        TestModels.loaded(SampleData.git()),
                        new SelectionMsg.NoLogSelected(new NoLog.Detached(new Sha("89eeda75aaaa"))))
                .model();

        assertEquals(
                dim("Log", "HEAD detached at 89eeda7: its commits are in the commits pane."),
                ContentView.of(detached, Scopes.BRANCHES.pane()));
    }

    @Test
    void theBranchesKeepShowingThePreviousLogWhileTheNewOneLoads() {
        Shown.Area log = area(ContentView.of(TestModels.switchingBranch(model, "other"), Scopes.BRANCHES.pane()));

        assertEquals("Log: feature/initial-layout", log.title());
        assertTrue(shortShas(log).contains("0a1b2c3"), log.toString());
    }

    @Test
    void aPopupThatJustClosedKeepsShowingTheBranchLogUntilTheFocusIsBack() {
        // The popup is gone from the model, but has the focus until the render gives it back.
        Model closed = model.mapRepoView(repoView -> repoView.withFocusedPane(Option.of(Scopes.BRANCHES.pane())));

        assertEquals(
                "Log: feature/initial-layout",
                area(ContentView.of(closed, new Focus.Aside("new-branch"))).title());
    }

    @Test
    void aPopupOpenedFromTheBranchesKeepsShowingTheBranchLog() {
        Model withPopup = model.mapRepoView(repoView -> repoView.withFocusedPane(Option.of(Scopes.BRANCHES.pane())))
                .openPopup(new HelpPopup(Scopes.BRANCHES.id(), List.empty()));

        assertEquals(
                "Log: feature/initial-layout",
                area(ContentView.of(withPopup, new Focus.Aside("help"))).title());
    }

    @Test
    void aPopupOpenedFromTheCommitChangesKeepsShowingTheCommit() {
        // The area had the focus when the popup opened, so that is what the focus goes back to.
        Model withPopup = showingCommit(DiffOptions.DEFAULT, "")
                .mapRepoView(repoView -> repoView.withFocusedPane(Option.of(CommitSource.AREA.id())))
                .openPopup(new HelpPopup(CommitSource.AREA.id().value(), List.empty()));

        assertEquals(
                "Commit: aaaa",
                area(ContentView.of(withPopup, new Focus.Aside("help"))).title());
    }

    @Test
    void theCommitsShowTheHighlightedCommitAndWhatItChanged() {
        Model selected = showingCommit(DiffOptions.DEFAULT, "+added line\n");
        Commit commit = selected.commit(new Sha("aaaa")).get();

        assertEquals(
                new Shown.Area(
                        CommitSource.AREA,
                        "Commit: aaaa",
                        new ScrolledContent.CommitChanges(commit, DiffText.of("+added line\n")),
                        new CommitBody(
                                commit,
                                new ShownChanges(
                                        ContentViewFixtures.whole("+added line\n"), ContentViewFixtures.PLAIN_LOOK))),
                ContentView.of(selected, CommitsPanel.PANE));
    }

    @Test
    void aCommitThatOnlyChangedWhitespaceSaysSoWhileIgnoringIt() {
        Shown.Area commit = area(
                ContentView.of(showingCommit(DiffOptions.DEFAULT.withIgnoreWhitespace(true), ""), CommitsPanel.PANE));

        assertEquals("Commit: aaaa · ignoring whitespace", commit.title());
        assertEquals(
                "No changes besides whitespace\n",
                assertInstanceOf(CommitBody.class, commit.body())
                        .changes()
                        .diff()
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
                        DivergenceSource.AREA,
                        "Commit: cccc",
                        new ScrolledContent.CommitChanges(plain("cccc"), DiffText.of("+added line")),
                        new CommitBody(
                                plain("cccc"),
                                new ShownChanges(
                                        ContentViewFixtures.whole("+added line"), ContentViewFixtures.PLAIN_LOOK))),
                ContentView.of(selected, Scopes.BRANCHES.pane()));
    }

    @Test
    void theDivergenceShowsLoadingWhileItsCommitIsReadAndOnlyOnTheLocalTab() {
        Model loading = divergenceHighlighted();

        assertEquals(loading("Commit"), ContentView.of(loading, Scopes.BRANCHES.pane()));
        Model remoteTab = loading.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.REMOTE));
        // The remote branches show the log of the branch, which none is selected for here.
        assertEquals(loading("Log"), ContentView.of(remoteTab, Scopes.BRANCHES.pane()));
    }

    @Test
    void aDivergenceWithoutCommitsSaysTheBranchesHaveNotDiverged() {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView ->
                        repoView.mapBranches(branches -> branches.withDivergence(Option.of(new DivergenceView(
                                new BranchName("main"),
                                new BranchRef.Local(new BranchName("feature")),
                                DivergenceView.Against.BASE,
                                new Loadable.Loaded<>(new Divergence(
                                        new Divergence.Side(List.of(), 0), new Divergence.Side(List.of(), 0))),
                                Option.none())))));

        assertEquals(dim("Commit", "The branches have not diverged"), ContentView.of(diverged, Scopes.BRANCHES.pane()));
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

        assertEquals(loading("Commit"), ContentView.of(loading, Scopes.BRANCHES.pane()));
        Shown.Area commit = area(ContentView.of(selected, Scopes.BRANCHES.pane()));
        assertEquals(BranchCommitsSource.AREA, commit.area());
        assertEquals("Commit: ffff", commit.title());
        assertEquals(
                "+added line",
                assertInstanceOf(CommitBody.class, commit.body())
                        .changes()
                        .diff()
                        .text());
    }

    @Test
    void withoutCommitsTheCommitsSaySoDimmed() {
        Model empty = TestModels.loaded().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.empty())));

        assertEquals(dim("Commit", "No commits"), ContentView.of(empty, CommitsPanel.PANE));
    }

    @Test
    void aBranchWithoutCommitsSaysSoDimmed() {
        assertEquals(
                dim("Commit", "No commits"), ContentView.of(branchCommitsOpen(List.empty()), Scopes.BRANCHES.pane()));
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
                        StashSource.AREA,
                        "Stash: stash@{0}",
                        new ScrolledContent.StashChanges(stash.sha(), DiffText.of("+added line\n")),
                        new StashBody(
                                stash,
                                new ShownChanges(
                                        ContentViewFixtures.whole("+added line\n"), ContentViewFixtures.PLAIN_LOOK))),
                ContentView.of(selected, StashPanel.PANE));
    }

    @Test
    void withoutStashesTheStashesSaySo() {
        Model clean = Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of()))
                .model();

        assertEquals(dim("Stash", "No stash entries"), ContentView.of(clean, StashPanel.PANE));
        assertEquals(List.empty(), ContentView.of(clean, StashPanel.PANE).areas());
    }

    @Test
    void withoutAHighlightedPullRequestThePullRequestsScreenSaysSo() {
        Shown shown = ContentView.of(pullRequests(Option.none()), PrOverview.SCREEN.first());

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
                        PrOverview.SCREEN.first()));
        assertEquals(
                new Shown.Message("Pull request", Placeholders.error(failure), Tone.ERROR),
                ContentView.of(
                        pullRequests(Option.of(new Preview<>(PULL_REQUEST, new Loadable.Failed<>(failure)))),
                        PrOverview.SCREEN.first()));
    }

    @Test
    void aReadPullRequestShowsWhatItSaysAndChanges() {
        Model read = pullRequests(Option.of(new Preview<>(PULL_REQUEST, new Loadable.Loaded<>(READ))));

        assertEquals(
                new Shown.Area(
                        PullRequestSource.AREA,
                        "Pull request: owner/repo#42",
                        new PullRequestSource.Read(PULL_REQUEST, READ),
                        new PullRequestBody(PULL_REQUEST, READ)),
                ContentView.of(read, PrOverview.SCREEN.first()));
    }

    @Test
    void thePullRequestsScreenShowsThePullRequestWhicheverPaneHasTheFocus() {
        Model read = pullRequests(Option.of(new Preview<>(PULL_REQUEST, new Loadable.Loaded<>(READ))));

        assertEquals(ContentView.of(read, PrOverview.SCREEN.first()), ContentView.of(read, FilesPanel.PANE));
    }
}
