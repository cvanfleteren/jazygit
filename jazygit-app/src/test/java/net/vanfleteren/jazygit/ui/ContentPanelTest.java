package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import org.junit.jupiter.api.Test;

/**
 * Unit tests on {@link ContentPanel}, exercising its three view branches (file status,
 * branch log, commit diff) and boundary selection indices, without needing a live TUI runner.
 */
class ContentPanelTest {

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    private final Model model = TestModels.loaded(new SampleData(), "feature/initial-layout");

    private static Commit plain(String sha, String... parents) {
        return new Commit(
                new Sha(sha),
                "Ada",
                "a@e.com",
                Instant.EPOCH,
                sha,
                "",
                List.of(parents).map(Sha::new));
    }

    private static Commit merge(String sha, String... parents) {
        return plain(sha, parents);
    }

    /**
     * The model with commit aaaa highlighted and its detail, {@code changes}, loaded.
     */
    private static Model showingCommit(String changes) {
        return Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of(changes)))
                .model();
    }

    @Test
    void filesFocusedRendersFileStatusView() {
        StyledElement<?> panel = content.render(model, FilesPanel.ID);
        assertNotNull(panel);
    }

    @Test
    void filesFocusedWithoutChangedFilesSaysSoInsteadOfLoading() throws Exception {
        Model clean = TestModels.loaded();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(clean, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(clean, FilesPanel.ID));
            assertTrue(screen.contains("No changed files"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void filesFocusedStillLoadsWhileTheStatusIsLoading() throws Exception {
        Model loading = TestModels.loading("repo");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(loading, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(loading, FilesPanel.ID));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void branchesFocusedRendersTheLogOfTheSelectedBranch() throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(model, BranchesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(model, BranchesPanel.ID));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
            assertTrue(screen.contains("commit 0a1b2c3"), screen);
            assertFalse(screen.contains("a1b2c3d"), screen);
        }
    }

    @Test
    void aPopupThatJustClosedKeepsShowingTheBranchLogUntilTheFocusIsBack() throws Exception {
        // The popup is gone from the model, but has the focus until the render gives it back.
        Model closed = model.mapRepoView(repoView -> repoView.withFocusedPane(Option.of(BranchesPanel.ID)));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(closed, "new-branch"))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(closed, "new-branch"));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
        }
    }

    @Test
    void aPopupOpenedFromTheBranchesKeepsShowingTheBranchLog() throws Exception {
        Model withPopup = model.mapRepoView(repoView -> repoView.withFocusedPane(Option.of(BranchesPanel.ID)))
                .openPopup(new net.vanfleteren.jazygit.feature.help.HelpPopup(BranchesPanel.ID, List.empty()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(withPopup, "help"))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(withPopup, "help"));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
        }
    }

    @Test
    void aPopupOpenedFromTheCommitChangesKeepsShowingTheCommit() throws Exception {
        Model selected = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of("")))
                .model();
        // The area had the focus when the popup opened, so that is what the focus goes back to.
        Model withPopup = selected.mapRepoView(repoView -> repoView.withFocusedPane(Option.of("commit-changes")))
                .openPopup(new net.vanfleteren.jazygit.feature.help.HelpPopup("commit-changes", List.empty()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(withPopup, "help"))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(withPopup, "help"));
            assertTrue(screen.contains("Commit: aaaa"), screen);
        }
    }

    @Test
    void branchesFocusedShowsLoadingBeforeABranchIsSelected() throws Exception {
        Model unselected = TestModels.loaded(new SampleData());
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(unselected, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(unselected, BranchesPanel.ID));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void branchesFocusedKeepsShowingThePreviousLogWhileTheNewOneLoads() throws Exception {
        Model switching = TestModels.switchingBranch(model, "other");
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(switching, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(switching, BranchesPanel.ID));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
            assertTrue(screen.contains("commit 0a1b2c3"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void commitsFocusedRendersCommitDiffView() {
        StyledElement<?> panel = content.render(model, CommitsPanel.ID);
        assertNotNull(panel);
    }

    @Test
    void unknownFocusFallsBackToFileStatusView() {
        StyledElement<?> panel = content.render(model, "unknown");
        assertNotNull(panel);
    }

    @Test
    void nothingFocusedBeforeTheFirstRenderFallsBackToFileStatusView() {
        StyledElement<?> panel = content.render(model, null);
        assertNotNull(panel);
    }

    @Test
    void rendersPlaceholdersWhileLoading() {
        Model loading = TestModels.loading("repo");
        assertNotNull(content.render(loading, FilesPanel.ID));
        assertNotNull(content.render(loading, BranchesPanel.ID));
        assertNotNull(content.render(loading, CommitsPanel.ID));
    }

    @Test
    void theCommitAreaCanBeFocusedOnceItShowsTheHighlightedCommitAndReturnsToTheCommitsPane() {
        Model selected = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();

        String area = content.areaOfPane(selected, CommitsPanel.ID).get();
        assertEquals(Option.of(CommitsPanel.ID), content.paneOfArea(selected, area));
        assertNotNull(content.render(selected, area));
    }

    @Test
    void ignoringWhitespaceSaysSoAndThatOnlyWhitespaceChanged() throws Exception {
        DiffOptions ignoring = DiffOptions.DEFAULT.withIgnoreWhitespace(true);
        Model dirty = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(TestModels.DIRTY))
                .model()
                .mapPreferences(preferences -> preferences.withDiffOptions(ignoring));
        List<FileEntry> files = TestModels.DIRTY.files();
        Model selected =
                Update.update(dirty, new SelectionMsg.FilesSelected(files)).model();
        Model blank = Update.update(selected, new LoadMsg.FileDiffLoaded(files, ignoring, new Diffs("", "")))
                .model();
        Model changed = Update.update(selected, new LoadMsg.FileDiffLoaded(files, ignoring, new Diffs("", "+a\n")))
                .model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(blank, FilesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(blank, FilesPanel.ID));
            assertTrue(screen.contains("Diff · ignoring whitespace"), screen);
            assertTrue(screen.contains("No changes besides whitespace"), screen);

            String diff = RenderedText.ofLarge(testRunner, () -> content.render(changed, FilesPanel.ID));
            assertTrue(diff.contains("Unstaged changes · ignoring whitespace"), diff);
        }
    }

    @Test
    void aCommitThatOnlyChangedWhitespaceSaysSoWhileIgnoringIt() throws Exception {
        DiffOptions ignoring = DiffOptions.DEFAULT.withIgnoreWhitespace(true);
        Model selected = Update.update(
                        Update.update(
                                        TestModels.loaded()
                                                .mapPreferences(preferences -> preferences.withDiffOptions(ignoring)),
                                        new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), ignoring, DiffText.of("")))
                .model();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(selected, CommitsPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(selected, CommitsPanel.ID));
            assertTrue(screen.contains("Commit: aaaa · ignoring whitespace"), screen);
            assertTrue(screen.contains("No changes besides whitespace"), screen);
        }
    }

    @Test
    void withADivergenceOpenTheBranchesShowItsHighlightedCommitWhichCanBeFocused() throws Exception {
        Commit ours = plain("cccc");
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView -> repoView.withDivergence(Option.of(new DivergenceView(
                        new Branch.Name("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("main"))),
                        DivergenceView.Against.UPSTREAM,
                        new Loadable.Loaded<>(new Divergence(List.of(ours), 1, List.of(), 0)),
                        Option.none()))));
        Model selected = Update.update(
                        Update.update(diverged, new SelectionMsg.DivergenceCommitSelected(new Sha("cccc")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("cccc"), DiffOptions.DEFAULT, DiffText.of("+added line")))
                .model();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(selected, BranchesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(selected, BranchesPanel.ID));
            assertTrue(screen.contains("Commit: cccc"), screen);
            assertTrue(screen.contains("+added line"), screen);
            assertFalse(screen.contains("Log:"), screen);
        }

        String area = content.areaOfPane(selected, BranchesPanel.ID).get();
        assertEquals(Option.of(BranchesPanel.ID), content.paneOfArea(selected, area));
    }

    @Test
    void withTheCommitsOfABranchOpenTheBranchesShowTheHighlightedOneWhichCanBeFocused() throws Exception {
        BranchRef feature = new BranchRef.Local(new Branch.Name("feature"));
        Model logRead = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.BranchSelected(feature))
                                .model(),
                        new LoadMsg.BranchLogLoaded(feature, TestModels.FEATURE_COMMITS))
                .model();
        Model open =
                Update.update(logRead, new BranchCommitsMsg.Opened(feature)).model();
        Model loading = Update.update(open, new SelectionMsg.BranchCommitSelected(new Sha("ffff")))
                .model();
        Model selected = Update.update(
                        loading,
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("ffff"), DiffOptions.DEFAULT, DiffText.of("+added line")))
                .model();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(selected, BranchesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(selected, BranchesPanel.ID));
            assertTrue(screen.contains("Commit: ffff"), screen);
            assertTrue(screen.contains("+added line"), screen);
            assertFalse(screen.contains("Log:"), screen);
        }

        assertEquals(Option.none(), content.areaOfPane(loading, BranchesPanel.ID));
        String area = content.areaOfPane(selected, BranchesPanel.ID).get();
        assertEquals(Option.of(BranchesPanel.ID), content.paneOfArea(selected, area));
    }

    @Test
    void aDivergenceWithoutCommitsSaysTheBranchesHaveNotDiverged() throws Exception {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView -> repoView.withDivergence(Option.of(new DivergenceView(
                        new Branch.Name("main"),
                        new BranchRef.Local(new Branch.Name("feature")),
                        DivergenceView.Against.BASE,
                        new Loadable.Loaded<>(new Divergence(List.of(), 0, List.of(), 0)),
                        Option.none()))));

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> content.render(diverged, BranchesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(diverged, BranchesPanel.ID));
            assertTrue(screen.contains("The branches have not diverged"), screen);
        }
    }

    @Test
    void stashFocusedShowsTheHighlightedStashUnderAHeaderWhichCanBeFocused() throws Exception {
        Stash stash = new Stash("stash@{0}", new Sha("5555"), "my work", Option.of("main"), Instant.EPOCH);
        Model stashed = Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of(stash)))
                .model();
        Model selected = Update.update(
                        Update.update(stashed, new SelectionMsg.StashSelected(stash.sha()))
                                .model(),
                        new LoadMsg.StashDetailLoaded(
                                stash.sha(), DiffOptions.DEFAULT, DiffText.of(" a.txt | 1 +\n\n+added line\n")))
                .model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(selected, StashPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(selected, StashPanel.ID));
            assertTrue(screen.contains("Stash: stash@{0}"), screen);
            assertTrue(screen.contains("stash@{0}: my work"), screen);
            assertTrue(screen.contains("Branch: main"), screen);
            assertTrue(screen.contains("Date: "), screen);
            assertTrue(screen.contains(" a.txt | 1 +"), screen);
            assertTrue(screen.contains("+added line"), screen);
        }

        String area = content.areaOfPane(selected, StashPanel.ID).get();
        assertTrue(ContentPanel.isArea(area));
        assertEquals(Option.of(StashPanel.ID), content.paneOfArea(selected, area));
    }

    @Test
    void stashFocusedWithoutStashesSaysSo() throws Exception {
        Model clean = Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of()))
                .model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(clean, StashPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(clean, StashPanel.ID));
            assertTrue(screen.contains("No stash entries"), screen);
            assertFalse(screen.contains("Unstaged changes"), screen);
        }
        assertEquals(Option.none(), content.areaOfPane(clean, StashPanel.ID));
    }

    @Test
    void stashFocusedWhileTheStashesLoadSaysSo() throws Exception {
        Model loading = TestModels.loading("repo");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(loading, StashPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(loading, StashPanel.ID));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void scrollingTellsTheModelAndTheAreaShowsWhereTheModelHasIt() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        String changes = List.range(0, 100).map(i -> "+line " + i).mkString("\n");
        Model selected = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of(changes)))
                .model();
        String area = scrolling.areaOfPane(selected, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(dev.tamboui.tui.event.KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        Msg.ContentScrolled down = (Msg.ContentScrolled) dispatched.getLast();
        assertEquals(area, down.area());
        assertEquals(1, down.to().row());

        // Where the model has it scrolled to is what shows, whatever was scrolled in the element before.
        Model scrolled = selected.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(down.to().content(), 20, 0)));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(scrolled, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> scrolling.render(scrolled, area));
            assertFalse(screen.contains("commit aaaa"), screen);
            assertTrue(screen.contains("+line 20"), screen);
        }
    }

    @Test
    void aFocusedAreaScrollsSidewaysLeavesOnEscapeAndLeavesOtherKeysToTheApp() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<KeyEvent> global = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                dispatched::add);
        Model selected = showingCommit("+" + "x".repeat(300));
        String area = scrolling.areaOfPane(selected, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            testRunner.runner().eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent key) {
                    global.add(key);
                }
                return EventResult.UNHANDLED;
            });
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press('x');
            SettledPilot.of(testRunner).press(KeyCode.RIGHT);
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(
                java.util.List.of('x'),
                List.ofAll(global).map(KeyEvent::character).toJavaList());
        assertEquals(1, leaves.get());
        Msg.ContentScrolled right = (Msg.ContentScrolled) dispatched.getLast();
        assertEquals(new Scrolled(right.to().content(), 0, 1), right.to());
    }

    @Test
    void theNavigationKeysScrollAFocusedArea() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                showingCommit(List.range(0, 100).map(i -> "+" + "x".repeat(300)).mkString("\n")));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        String area = scrolling.areaOfPane(model.get(), CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.of(
                            KeyCode.END,
                            KeyCode.UP,
                            KeyCode.PAGE_UP,
                            KeyCode.HOME,
                            KeyCode.PAGE_DOWN,
                            KeyCode.RIGHT,
                            KeyCode.RIGHT,
                            KeyCode.LEFT)
                    .forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
        }

        List<Scrolled> at = List.ofAll(dispatched).map(msg -> ((Msg.ContentScrolled) msg).to());
        int bottom = at.get(0).row();
        int page = at.get(4).row();
        assertTrue(bottom > 0 && page > 1, at.toString());
        assertEquals(List.of(bottom, bottom - 1, bottom - 1 - page, 0, page, page, page, page), at.map(Scrolled::row));
        assertEquals(List.of(0, 0, 0, 0, 0, 1, 2, 1), at.map(Scrolled::column));
    }

    @Test
    void escapeGoesToTheAppWhenTheAreaCannotBeLeft() throws Exception {
        java.util.List<KeyEvent> global = new java.util.ArrayList<>();
        Model selected = showingCommit("+added line");
        String area = content.areaOfPane(selected, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(selected, area))) {
            testRunner.runner().eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent key) {
                    global.add(key);
                }
                return EventResult.UNHANDLED;
            });
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> content.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(
                java.util.List.of(KeyCode.ESCAPE),
                List.ofAll(global).map(KeyEvent::code).toJavaList());
    }

    @Test
    void theAreaScrollsOnFromWhereTheModelHasItScrolledSideways() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model selected =
                showingCommit(List.range(0, 100).map(i -> "+" + "x".repeat(300)).mkString("\n"));
        String area = scrolling.areaOfPane(selected, CommitsPanel.ID).get();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        ScrolledContent shown =
                ((Msg.ContentScrolled) dispatched.getLast()).to().content();

        // TamboUI does not draw the columns scrolled past, so the next scroll tells where the area was.
        Model scrolled = selected.withScrolled(io.vavr.collection.HashMap.of(area, new Scrolled(shown, 0, 11)));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(scrolled, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(scrolled, area));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        assertEquals(new Scrolled(shown, 1, 11), ((Msg.ContentScrolled) dispatched.getLast()).to());
    }

    @Test
    void theCommitAreaCannotBeFocusedWhileItsDetailLoadsOrItsCommitIsGone() {
        Model loading = Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, CommitsPanel.ID));

        Model gone = showingCommit("changes").mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.empty())));
        assertEquals(Option.none(), content.areaOfPane(gone, CommitsPanel.ID));
    }

    @Test
    void theStashAreaCannotBeFocusedWhileItsDetailLoadsOrItsStashIsGone() {
        Stash stash = new Stash("stash@{0}", new Sha("5555"), "my work", Option.of("main"), Instant.EPOCH);
        Model loading = Update.update(
                        Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(List.of(stash)))
                                .model(),
                        new SelectionMsg.StashSelected(stash.sha()))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, StashPanel.ID));

        Model loaded = Update.update(
                        loading, new LoadMsg.StashDetailLoaded(stash.sha(), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        assertTrue(content.areaOfPane(loaded, StashPanel.ID).isDefined());
        Model gone = loaded.mapRepo(repo -> repo.withStashes(new Loadable.Loaded<>(List.empty())));
        assertEquals(Option.none(), content.areaOfPane(gone, StashPanel.ID));
    }

    @Test
    void theDivergenceAreaCanOnlyBeFocusedOnTheLocalTabOnceItsCommitIsLoaded() {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView -> repoView.withDivergence(Option.of(new DivergenceView(
                        new Branch.Name("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("main"))),
                        DivergenceView.Against.UPSTREAM,
                        new Loadable.Loaded<>(new Divergence(List.of(plain("cccc")), 1, List.of(), 0)),
                        Option.none()))));
        Model loading = Update.update(diverged, new SelectionMsg.DivergenceCommitSelected(new Sha("cccc")))
                .model();
        assertEquals(Option.none(), content.areaOfPane(loading, BranchesPanel.ID));

        Model loaded = Update.update(
                        loading,
                        new LoadMsg.CommitDetailLoaded(new Sha("cccc"), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        assertTrue(content.areaOfPane(loaded, BranchesPanel.ID).isDefined());
        Model remoteTab = loaded.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.REMOTE));
        assertEquals(Option.none(), content.areaOfPane(remoteTab, BranchesPanel.ID));
    }

    @Test
    void theDiffAreasReturnToTheFilesPane() {
        Model model = TestModels.loaded();
        assertEquals(Option.none(), content.paneOfArea(model, "unknown"));
        assertEquals(Option.of(FilesPanel.ID), content.paneOfArea(model, "diff-staged"));
        assertEquals(Option.of(FilesPanel.ID), content.paneOfArea(model, "diff-unstaged"));
    }

    @Test
    void commitTextShowsTheHeaderTheMessageADividerAndTheChanges() {
        Commit commit = new Commit(
                new Sha("a1b2c3d4e5f6"),
                "Ada Lovelace",
                "ada@example.com",
                Instant.parse("2026-10-09T12:30:00Z"),
                "Subject",
                "More\ntext");

        List<String> lines =
                ContentPanel.commitText(commit, " a.txt | 1 +\n 1 file changed\n\n+added\n", Theme.SpanStyles.NONE)
                        .lines()
                        .stream()
                        .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                        .collect(List.collector());

        assertEquals("commit a1b2c3d4e5f6", lines.get(0));
        assertEquals("Author: Ada Lovelace <ada@example.com>", lines.get(1));
        assertTrue(lines.get(2).startsWith("Date: "), lines.get(2));
        assertEquals(
                List.of(
                        "",
                        "    Subject",
                        "",
                        "    More",
                        "    text",
                        "---",
                        " a.txt | 1 +",
                        " 1 file changed",
                        "",
                        "+added"),
                lines.drop(3));
    }

    @Test
    void aPullRequestShowsLikeACommitWithItsBranchesItsDescriptionAndItsDiff() {
        PullRequestSummary pr = new PullRequestSummary(
                "owner/repo",
                12,
                "Fix the login",
                "alice",
                "fix/login",
                false,
                true,
                PullRequest.Checks.NONE,
                PullRequest.Review.NONE,
                Instant.parse("2026-10-09T12:30:00Z"),
                "https://github.com/owner/repo/pull/12");

        List<String> lines = ContentPanel.pullRequestText(
                        pr,
                        new PullRequestPreview("main", "Why:\r\n\r\nit broke", "+fixed\n", false),
                        Theme.SpanStyles.NONE)
                .lines()
                .stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals("pull request owner/repo#12", lines.get(0));
        assertEquals("Opened by alice", lines.get(1));
        assertEquals("From fix/login, in a fork, into main", lines.get(2));
        assertTrue(lines.get(3).startsWith("Updated "), lines.get(3));
        assertEquals(
                List.of("", "    Fix the login", "", "    Why:", "", "    it broke", "---", "+fixed"), lines.drop(4));
    }

    @Test
    void commitTextHasNoBlankLineWithoutAnExtendedMessage() {
        Commit commit = new Commit(new Sha("a1b2c3d4e5f6"), "Ada", "ada@example.com", Instant.EPOCH, "Subject", "");

        List<String> lines = ContentPanel.commitText(commit, "x", Theme.SpanStyles.NONE).lines().stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals(List.of("", "    Subject", "---", "x"), lines.drop(3));
    }

    @Test
    void aTruncatedDiffEndsWithALineSayingSo() {
        assertEquals("+a\n", ContentPanel.shown(DiffText.of("+a\n")));
        assertEquals("+a\n… truncated\n", ContentPanel.shown(new DiffText("+a\n", true)));
    }

    @Test
    void outOfRangeSelectionIsClampedRatherThanThrowing() {
        assertDoesNotThrow(() -> content.render(model, CommitsPanel.ID));
        assertDoesNotThrow(() -> content.render(model, CommitsPanel.ID));
    }

    @Test
    void logLinesShowTheExtendedMessageFollowedByABlankLine() {
        Commit commit = new Commit(
                new Sha("a1b2c3d"),
                "Ada Lovelace",
                "ada@example.com",
                Instant.parse("2026-10-09T12:30:05Z"),
                "Add layout",
                "First detail\nSecond detail");

        assertEquals(
                List.of(
                        "* commit a1b2c3d",
                        "| Ada Lovelace <ada@example.com>",
                        "| 2026-10-09 14:30:05",
                        "| ",
                        "|     Add layout",
                        "| ",
                        "|     First detail",
                        "|     Second detail",
                        "| "),
                ContentPanel.logLines(List.of(commit), ZoneId.of("Europe/Brussels")));
    }

    @Test
    void logLinesDrawMergedBranchesWithRails() {
        List<Commit> log = net.vanfleteren.jazygit.git.model.CommitGraph.arrange(
                List.of(merge("m", "b", "f"), plain("f", "a"), plain("b", "a"), plain("a")));

        List<String> lines = ContentPanel.logLines(log, ZoneOffset.UTC);

        assertTrue(lines.contains("*   commit m"));
        assertTrue(lines.contains("|\\  Merge: b f"));
        assertTrue(lines.contains("| * commit f"));
        assertTrue(lines.contains("|/  Ada <a@e.com>"));
    }

    @Test
    void logLinesWithoutExtendedMessageEndAfterTheSubject() {
        List<Commit> commits = List.of(
                new Commit(
                        new Sha("a1b2c3d"),
                        "Ada",
                        "ada@example.com",
                        Instant.parse("2026-10-09T12:30:00Z"),
                        "Second",
                        ""),
                new Commit(
                        new Sha("b2c3d4e"),
                        "Ada",
                        "ada@example.com",
                        Instant.parse("2026-10-08T12:30:00Z"),
                        "First",
                        ""));

        assertEquals(
                List.of(
                        "* commit a1b2c3d",
                        "| Ada <ada@example.com>",
                        "| 2026-10-09 12:30:00",
                        "| ",
                        "|     Second",
                        "| ",
                        "* commit b2c3d4e",
                        "| Ada <ada@example.com>",
                        "| 2026-10-08 12:30:00",
                        "| ",
                        "|     First",
                        "| "),
                ContentPanel.logLines(commits, ZoneOffset.UTC));
    }

    @Test
    void filesFocusedShowsStagedAndUnstagedChangesInSeparatePanels() throws Exception {
        SampleData data = new SampleData();
        Model selected = TestModels.withFilesSelected(
                data,
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(selected, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(selected, FilesPanel.ID));
            assertTrue(screen.contains("Staged changes"), screen);
            assertTrue(screen.contains("Unstaged changes"), screen);
            assertTrue(screen.indexOf("Staged changes") < screen.indexOf("Unstaged changes"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }
}
