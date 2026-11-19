package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.github.PullRequestsPanel;
import net.vanfleteren.jazygit.ui.stash.StashPanel;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * Tests on {@link ContentPanel} as a whole: which areas it lays out for what {@link ContentView} shows,
 * which of them can be focused, and which pane each gives the focus back to. What each kind of content shows, and
 * how an area scrolls and draws, have test classes of their own.
 */
class ContentPanelTest {

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    @Test
    void filesFocusedWithoutChangedFilesSaysSoInsteadOfLoading() throws Exception {
        Model clean = TestModels.loaded();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(clean, FilesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> content.render(clean, FilesPanel.PANE));
            assertTrue(screen.contains("No changed files"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void theCommitAreaCanBeFocusedOnceItShowsTheHighlightedCommitAndReturnsToTheCommitsPane() {
        Model selected = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();

        AreaId area = content.areaOfPane(selected, CommitsPanel.PANE).get();
        assertEquals(Option.of(CommitsPanel.PANE), content.paneOfArea(selected, area));
        assertNotNull(content.render(selected, area));
    }

    @Test
    void theFirstFrameInAnotherSizeFillsTheAreaItIsDrawnIn() throws Exception {
        ContentPanel filling = new ContentPanel(ContentPanelFixtures.GREEN_ADDED, () -> false, id -> {}, msg -> {});
        Model model = ContentPanelFixtures.showingRichCommit("+short\n+a bit longer");
        AreaId area = filling.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> filling.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            ContentPanelFixtures.drawn(testRunner, () -> filling.render(model, area), 60);
            // Wider, as when the left column is made narrower: on to the new edge, not that of the frame before.
            ContentPanelFixtures.assertBackgroundTo(
                    Color.GREEN,
                    "+ short",
                    98,
                    ContentPanelFixtures.drawn(testRunner, () -> filling.render(model, area), 100));
            // Narrower again: nothing left of the frame before, like the background of a line it drew wider.
            ContentPanelFixtures.assertBackgroundTo(
                    Color.GREEN,
                    "+ short",
                    58,
                    ContentPanelFixtures.drawn(testRunner, () -> filling.render(model, area), 60));
        }
    }

    @Test
    void theStagedAndUnstagedChangesEachTakeHalfOfTheArea() throws Exception {
        Model selected = TestModels.withFilesSelected(
                SampleData.git(),
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(selected, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(selected, FilesPanel.PANE));
            List<String> rows = List.of(screen.split("\n"));
            int staged = rows.indexWhere(row -> row.contains("Staged changes"));
            int unstaged = rows.indexWhere(row -> row.contains("Unstaged changes"));
            // Each takes half of the 30 rows, not only as many as its first lines take.
            assertEquals(15, unstaged - staged, screen);
            assertEquals(2, rows.count(row -> row.contains("+new")), screen);
        }
    }

    @Test
    void theCommitAreaCannotBeFocusedWhileItsDetailLoadsOrItsCommitIsGone() {
        Model loading = Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, CommitsPanel.PANE));

        Model gone = ContentPanelFixtures.showingCommit("changes")
                .mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.empty())));
        assertEquals(Option.none(), content.areaOfPane(gone, CommitsPanel.PANE));
    }

    @Test
    void theStashAreaCannotBeFocusedWhileItsDetailLoadsOrItsStashIsGone() {
        Stash stash =
                new Stash("stash@{0}", new Sha("5555"), "my work", Option.of(new BranchName("main")), Instant.EPOCH);
        Model loading = Update.update(
                        Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of(stash)))
                                .model(),
                        new SelectionMsg.StashSelected(stash.sha()))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, StashPanel.PANE));

        Model loaded = Update.update(
                        loading, new LoadMsg.StashDetailLoaded(stash.sha(), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        assertTrue(content.areaOfPane(loaded, StashPanel.PANE).isDefined());
        Model gone = loaded.mapRepo(repo -> repo.withStashes(new Loadable.Loaded<>(List.empty())));
        assertEquals(Option.none(), content.areaOfPane(gone, StashPanel.PANE));
    }

    @Test
    void theDivergenceAreaCanOnlyBeFocusedOnTheLocalTabOnceItsCommitIsLoaded() {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView ->
                        repoView.mapBranches(branches -> branches.withDivergence(Option.of(new DivergenceView(
                                new BranchName("main"),
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))),
                                DivergenceView.Against.UPSTREAM,
                                new Loadable.Loaded<>(new Divergence(
                                        new Divergence.Side(List.of(ContentPanelFixtures.plain("cccc")), 1),
                                        new Divergence.Side(List.of(), 0))),
                                Option.none())))));
        Model loading = Update.update(diverged, new SelectionMsg.DivergenceCommitSelected(new Sha("cccc")))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, BranchesPanel.PANE));

        Model loaded = Update.update(
                        loading,
                        new LoadMsg.CommitDetailLoaded(new Sha("cccc"), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        assertTrue(content.areaOfPane(loaded, BranchesPanel.PANE).isDefined());
        Model remoteTab =
                loaded.mapRepoView(repoView -> repoView.mapBranches(branches -> branches.withTab(BranchesTab.REMOTE)));
        assertEquals(Option.none(), content.areaOfPane(remoteTab, BranchesPanel.PANE));
    }

    @Test
    void theBranchCommitAreaCanBeFocusedOnceItsCommitIsLoaded() {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        Model logRead = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.BranchSelected(feature))
                                .model(),
                        TestModels.branchLogLoaded(feature, TestModels.FEATURE_COMMITS))
                .model();
        Model open =
                Update.update(logRead, new BranchCommitsMsg.Opened(feature)).model();
        Model loading = Update.update(open, new SelectionMsg.BranchCommitSelected(new Sha("ffff")))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, BranchesPanel.PANE));

        Model loaded = Update.update(
                        loading,
                        new LoadMsg.CommitDetailLoaded(new Sha("ffff"), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        AreaId area = content.areaOfPane(loaded, BranchesPanel.PANE).get();
        assertEquals(Option.of(BranchesPanel.PANE), content.paneOfArea(loaded, area));
    }

    @Test
    void anAreaStylesItsBodyAgainOnlyOnceItOrTheThemeChanges() throws Exception {
        java.util.concurrent.atomic.AtomicInteger styled = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel counting = new ContentPanel(
                cssClass -> {
                    styled.incrementAndGet();
                    return dev.tamboui.style.Style.EMPTY;
                },
                () -> false);
        Model selected = ContentPanelFixtures.showingCommit("+added line\n");
        Model light = selected.mapPreferences(preferences ->
                preferences.withThemeOverride(Option.of(net.vanfleteren.jazygit.settings.ThemeName.LIGHT)));
        Model other = ContentPanelFixtures.showingCommit("+other line\n");

        counting.render(selected, CommitsPanel.PANE);
        int once = styled.get();
        counting.render(selected, CommitsPanel.PANE);
        assertEquals(once, styled.get());

        counting.render(light, CommitsPanel.PANE);
        assertTrue(styled.get() > once, "restyled in another theme");
        int inLight = styled.get();
        counting.render(other, CommitsPanel.PANE);
        assertTrue(styled.get() > inLight, "restyled for another body");
    }

    @Test
    void aClickOnTheLogGivesItTheFocus() throws Exception {
        java.util.List<Focus> focused = new java.util.ArrayList<>();
        ContentPanel clicking = new ContentPanel(Theme.SpanStyles.NONE, () -> false, focused::add, msg -> {});
        Model model = TestModels.loaded(SampleData.git(), "feature/initial-layout");

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> clicking.render(model, BranchesPanel.PANE))) {
            RenderedText.of(testRunner, () -> clicking.render(model, BranchesPanel.PANE));
            dev.tamboui.layout.Rect drawn = SettledPilot.of(testRunner).findElement("branch-log");

            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, drawn.y() + 2);
        }

        assertEquals(java.util.List.of(AreaId.LOG), focused);
    }

    @Test
    void theDiffAreasReturnToTheFilesPane() {
        Model model = TestModels.loaded();
        assertEquals(Option.none(), content.paneOfArea(model, new PaneId("unknown")));
        assertEquals(Option.of(FilesPanel.PANE), content.paneOfArea(model, AreaId.STAGED));
        assertEquals(Option.of(FilesPanel.PANE), content.paneOfArea(model, AreaId.UNSTAGED));
    }

    @Test
    void thePullRequestAreaIsFocusedFromTheListWhosePullRequestItShows() {
        PullRequestSummary pr = new PullRequestSummary(
                "owner/repo",
                new PrNumber(42),
                "Fix the login",
                "alice",
                new GhBranch("fix/login"),
                false,
                false,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/42");
        Model read = TestModels.loaded()
                .withScreen(Screen.PULL_REQUESTS)
                .withPrOverview(net.vanfleteren.jazygit.state.github.prs.PrOverview.INITIAL
                        .withHighlighted(Option.of(pr))
                        .withPreview(Option.of(new Preview<>(
                                pr,
                                new Loadable.Loaded<>(
                                        new PullRequestPreview(new GhBranch("main"), "Why", "+fixed\n", false))))));
        PaneId first = PullRequestsPanel.FIRST_PANE;
        PaneId second = net.vanfleteren.jazygit.ui.keys.Scopes.PR_REVIEW_REQUESTED.pane();

        // Before a list had the focus, the pull request is that of the first list.
        assertEquals(Option.of(AreaId.PULL_REQUEST), content.areaOfPane(read, first));
        assertEquals(Option.none(), content.areaOfPane(read, second));

        Model fromSecond = read.withPrOverview(read.prOverview().withListPane(Option.of(second)));
        assertEquals(Option.of(AreaId.PULL_REQUEST), content.areaOfPane(fromSecond, second));
        assertEquals(Option.none(), content.areaOfPane(fromSecond, first));
        assertEquals(Option.of(second), content.paneOfArea(fromSecond, AreaId.PULL_REQUEST));
    }

    @Test
    void filesFocusedShowsStagedAndUnstagedChangesInSeparatePanels() throws Exception {
        Git data = SampleData.git();
        Model selected = TestModels.withFilesSelected(
                data,
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(selected, FilesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> content.render(selected, FilesPanel.PANE));
            assertTrue(screen.contains("Staged changes"), screen);
            assertTrue(screen.contains("Unstaged changes"), screen);
            assertTrue(screen.indexOf("Staged changes") < screen.indexOf("Unstaged changes"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }
}
