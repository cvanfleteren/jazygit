package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * Tests on {@link ContentPanel}: how it draws what {@link ContentView} shows, styles the texts, and lets the areas
 * be focused and scrolled.
 */
class ContentPanelTest {

    private static final DiffLook PLAIN_LOOK = new DiffLook(DiffStyle.PLAIN, true, true);

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    private static Commit plain(String sha, String... parents) {
        return Fixtures.commit(
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
    void filesFocusedWithoutChangedFilesSaysSoInsteadOfLoading() throws Exception {
        Model clean = TestModels.loaded();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(clean, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(clean, FilesPanel.ID));
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

        String area = content.areaOfPane(selected, CommitsPanel.ID).get();
        assertEquals(Option.of(CommitsPanel.ID), content.paneOfArea(selected, area));
        assertNotNull(content.render(selected, area));
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
        Model scrolled = selected.withScrolled(io.vavr.collection.HashMap.of(
                area,
                new Scrolled(
                        down.to().content(), 20, 0, io.vavr.control.Option.none(), io.vavr.control.Option.none())));
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
        assertEquals(
                new Scrolled(right.to().content(), 0, 1, io.vavr.control.Option.none(), io.vavr.control.Option.none()),
                right.to());
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
    void leftLeavesADiffOfTheFilesPaneOnceItIsScrolledAllTheWayLeft() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(TestModels.withFilesSelected(
                        new SampleData(),
                        List.of(new FileEntry("x".repeat(300) + ".txt", ChangeType.MODIFIED, true, false))));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                msg -> {
                    dispatched.add(msg);
                    model.set(Update.update(model.get(), msg).model());
                });
        String area = scrolling.areaOfPane(model.get(), FilesPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.RIGHT);
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
        assertEquals(
                List.of(1, 0),
                List.ofAll(dispatched)
                        .map(msg -> ((Msg.ContentScrolled) msg).to().column()));
    }

    @Test
    void leftDoesNotLeaveTheAreasOfOtherPanes() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> {
            leaves.incrementAndGet();
            return true;
        });
        Model selected = showingCommit("+" + "x".repeat(300));
        String area = scrolling.areaOfPane(selected, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(0, leaves.get());
    }

    @Test
    void leftLeavesTheDiffOfAFileOfACommit() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> {
            leaves.incrementAndGet();
            return true;
        });
        CommitFilesView.Changes of = new CommitFilesView.Changes.OfCommit(new Sha("aaaa"));
        CommitFile file = new CommitFile("src/a.txt", ChangeType.MODIFIED);
        Model open = Update.update(
                        Update.update(TestModels.loaded(), new CommitFilesMsg.Opened(of, CommitsPanel.ID))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                .model();
        Model selected = Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), DiffOptions.DEFAULT, DiffText.of("+" + "x".repeat(300))))
                .model();
        String area = scrolling.areaOfPane(selected, FilesPanel.ID).get();
        assertEquals(ContentArea.COMMIT_FILE.id(), area);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
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
        Model scrolled = selected.withScrolled(io.vavr.collection.HashMap.of(
                area, new Scrolled(shown, 0, 11, io.vavr.control.Option.none(), io.vavr.control.Option.none())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(scrolled, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(scrolled, area));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        assertEquals(
                new Scrolled(shown, 1, 11, io.vavr.control.Option.none(), io.vavr.control.Option.none()),
                ((Msg.ContentScrolled) dispatched.getLast()).to());
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
        Stash stash =
                new Stash("stash@{0}", new Sha("5555"), "my work", Option.of(new BranchName("main")), Instant.EPOCH);
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
                        new BranchName("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))),
                        DivergenceView.Against.UPSTREAM,
                        new Loadable.Loaded<>(new Divergence(
                                new Divergence.Side(List.of(plain("cccc")), 1), new Divergence.Side(List.of(), 0))),
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
        assertEquals(Option.none(), content.areaOfPane(loading, BranchesPanel.ID));

        Model loaded = Update.update(
                        loading,
                        new LoadMsg.CommitDetailLoaded(new Sha("ffff"), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        String area = content.areaOfPane(loaded, BranchesPanel.ID).get();
        assertEquals(Option.of(BranchesPanel.ID), content.paneOfArea(loaded, area));
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
        Model selected = showingCommit("+added line\n");
        Model light = selected.mapPreferences(preferences ->
                preferences.withThemeOverride(Option.of(net.vanfleteren.jazygit.settings.ThemeName.LIGHT)));
        Model other = showingCommit("+other line\n");

        counting.render(selected, CommitsPanel.ID);
        int once = styled.get();
        counting.render(selected, CommitsPanel.ID);
        assertEquals(once, styled.get());

        counting.render(light, CommitsPanel.ID);
        assertTrue(styled.get() > once, "restyled in another theme");
        int inLight = styled.get();
        counting.render(other, CommitsPanel.ID);
        assertTrue(styled.get() > inLight, "restyled for another body");
    }

    @Test
    void inTheRichViewTheKeysMoveTheCursorWithoutStylingTheDiffAgain() throws Exception {
        java.util.concurrent.atomic.AtomicInteger styled = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel counting = new ContentPanel(
                cssClass -> {
                    if (!cssClass.equals(Theme.Classes.DIFF_CURSOR)) {
                        styled.incrementAndGet();
                    }
                    return dev.tamboui.style.Style.EMPTY;
                },
                () -> false,
                id -> {},
                dispatched::add);
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model model = Update.update(
                        Update.update(rich, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"), rich.preferences().diffOptions(), DiffText.of("""
                                diff --git a/a.txt b/a.txt
                                --- a/a.txt
                                +++ b/a.txt
                                @@ -1 +1 @@
                                -a
                                +b
                                """)))
                .model();
        String area = counting.areaOfPane(model, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> counting.render(model, area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> counting.render(model, area));
            int once = styled.get();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();

            // From the header of the commit, its lead, to the bar of its file.
            Msg.ContentScrolled down = (Msg.ContentScrolled) dispatched.getLast();
            assertEquals(Option.of(new Anchor.File("a.txt")), down.to().cursor());
            assertEquals(0, down.to().row());
            Model moved = Update.update(model, down).model();
            RenderedText.ofLarge(testRunner, () -> counting.render(moved, area));
            assertEquals(once, styled.get());
        }
    }

    @Test
    void aClickPutsTheCursorOfARichDiffOnTheRowClickedButNotOnItsBorder() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel clicking = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model model = Update.update(
                        Update.update(rich, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"), rich.preferences().diffOptions(), DiffText.of("""
                                diff --git a/a.txt b/a.txt
                                --- a/a.txt
                                +++ b/a.txt
                                @@ -0,0 +1,3 @@
                                +alpha
                                +beta
                                +gamma
                                """)))
                .model();
        String area = clicking.areaOfPane(model, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> clicking.render(model, area))) {
            java.util.List<String> screen = RenderedText.ofLarge(testRunner, () -> clicking.render(model, area))
                    .lines()
                    .toList();
            int beta = java.util.stream.IntStream.range(0, screen.size())
                    .filter(row -> screen.get(row).contains("beta"))
                    .findFirst()
                    .getAsInt();
            dev.tamboui.layout.Rect drawn = SettledPilot.of(testRunner).findElement(area);

            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, beta);
            Msg.ContentScrolled clicked = (Msg.ContentScrolled) dispatched.getLast();
            assertEquals(
                    Option.of(new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 2)),
                    clicked.to().cursor());
            assertEquals(0, clicked.to().row());

            int sent = dispatched.size();
            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, drawn.y());
            assertEquals(sent, dispatched.size());
        }
    }

    @Test
    void theKeysOfTheDiffOfAPullRequestActOnItsCursor() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel reviewing = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model model = pullRequestDiff();
        String area = reviewing.areaOfPane(model, FilesPanel.ID).get();

        // The model the app draws, which only knows where the cursor is once it handled the message that says so.
        java.util.concurrent.atomic.AtomicReference<Model> drawn =
                new java.util.concurrent.atomic.AtomicReference<>(model);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(drawn.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            Msg.ContentScrolled next = (Msg.ContentScrolled) dispatched.getLast();
            Anchor onThread = new Anchor.Thread(
                    "T1",
                    Option.of(new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 1)));
            assertEquals(Option.of(onThread), next.to().cursor());

            drawn.set(Update.update(model, next).model());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('x');
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    onThread,
                    ((net.vanfleteren.jazygit.feature.github.review.ReviewMsg.ResolveAsked) dispatched.getLast())
                            .at()
                            .cursor());

            // Up to its line, and a thread started there, before the model knows the cursor moved.
            SettledPilot.of(testRunner).press('k');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('c');
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 1),
                    ((net.vanfleteren.jazygit.feature.github.review.ReviewMsg.StartAsked) dispatched.getLast())
                            .at()
                            .cursor());
        }
    }

    @Test
    void leftDropsAnOpenRangeBeforeItLeavesTheDiffAsEscapeDoes() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(pullRequestDiff());
        ContentPanel reviewing = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                msg -> {
                    dispatched.add(msg);
                    model.set(Update.update(model.get(), msg).model());
                });
        String area = reviewing.areaOfPane(model.get(), FilesPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            // On to the thread, up to its line, and a range opened there.
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('k');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
            assertTrue(((Msg.ContentScrolled) dispatched.getLast()).to().mark().isDefined());

            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            assertEquals(
                    Option.none(),
                    ((Msg.ContentScrolled) dispatched.getLast()).to().mark());

            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
    }

    /**
     * A pull request's files open in the files pane, in the rich view, its one file selected: a diff of one added
     * line, with a thread on it.
     */
    private static Model pullRequestDiff() {
        CommitFilesView.Changes of = new CommitFilesView.Changes.OfPullRequest(
                new PullRequestCommits(7, "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40))));
        CommitFile file = new CommitFile("a.txt", ChangeType.MODIFIED);
        net.vanfleteren.jazygit.gh.model.ReviewThread thread = new net.vanfleteren.jazygit.gh.model.ReviewThread(
                "a.txt",
                net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT,
                Option.of(1),
                Option.none(),
                net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT,
                Option.of(1),
                false,
                false,
                net.vanfleteren.jazygit.gh.model.ReviewThread.Subject.LINE,
                List.of(new net.vanfleteren.jazygit.gh.model.ReviewThread.Comment(
                        "bob", Instant.EPOCH, "Why?", Option.none(), "C", "1")),
                0,
                "T1",
                net.vanfleteren.jazygit.gh.model.ReviewThread.Rights.ALL);
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model open = Update.update(
                        Update.update(
                                        Update.update(
                                                        rich,
                                                        new net.vanfleteren.jazygit.feature.diff.CommitFilesMsg.Opened(
                                                                of, BranchesPanel.ID))
                                                .model(),
                                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                                .model(),
                        new net.vanfleteren.jazygit.feature.diff.CommitFilesMsg.RemarksLoaded(
                                7,
                                new net.vanfleteren.jazygit.gh.model.ReviewRemarks(
                                        net.vanfleteren.jazygit.gh.model.PullRequestDetails.State.OPEN,
                                        net.vanfleteren.jazygit.gh.model.GhCommit.parse("a".repeat(40)),
                                        net.vanfleteren.jazygit.gh.model.GhCommit.parse("b".repeat(40)),
                                        List.of(thread))))
                .model();
        return Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), rich.preferences().diffOptions(), DiffText.of("""
                                diff --git a/a.txt b/a.txt
                                --- a/a.txt
                                +++ b/a.txt
                                @@ -0,0 +1 @@
                                +a
                                """)))
                .model();
    }

    @Test
    void theDiffAreasReturnToTheFilesPane() {
        Model model = TestModels.loaded();
        assertEquals(Option.none(), content.paneOfArea(model, "unknown"));
        assertEquals(Option.of(FilesPanel.ID), content.paneOfArea(model, "diff-staged"));
        assertEquals(Option.of(FilesPanel.ID), content.paneOfArea(model, "diff-unstaged"));
    }

    @Test
    void thePullRequestAreaIsFocusedFromTheListWhosePullRequestItShows() {
        PullRequestSummary pr = new PullRequestSummary(
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
        Model read = TestModels.loaded()
                .withScreen(Screen.PULL_REQUESTS)
                .withPrOverview(net.vanfleteren.jazygit.state.prs.PrOverview.INITIAL
                        .withHighlighted(Option.of(pr))
                        .withPreview(Option.of(new Preview<>(
                                pr, new Loadable.Loaded<>(new PullRequestPreview("main", "Why", "+fixed\n", false))))));
        String first = PullRequestsPanel.FIRST_PANE_ID;
        String second = net.vanfleteren.jazygit.keys.Scopes.PR_REVIEW_REQUESTED.id();

        // Before a list had the focus, the pull request is that of the first list.
        assertEquals(Option.of("pull-request-preview"), content.areaOfPane(read, first));
        assertEquals(Option.none(), content.areaOfPane(read, second));

        Model fromSecond = read.withPrOverview(read.prOverview().withListPane(Option.of(second)));
        assertEquals(Option.of("pull-request-preview"), content.areaOfPane(fromSecond, second));
        assertEquals(Option.none(), content.areaOfPane(fromSecond, first));
        assertEquals(Option.of(second), content.paneOfArea(fromSecond, "pull-request-preview"));
    }

    @Test
    void commitTextShowsTheHeaderTheMessageADividerAndTheChanges() {
        Commit commit = Fixtures.commit(
                new Sha("a1b2c3d4e5f6"),
                "Ada Lovelace",
                "ada@example.com",
                Instant.parse("2026-10-09T12:30:00Z"),
                "Subject",
                "More\ntext");

        List<String> lines = ContentPanel.commitText(
                        commit,
                        ContentView.shown(DiffText.of(" a.txt | 1 +\n 1 file changed\n\n+added\n")),
                        PLAIN_LOOK,
                        Theme.SpanStyles.NONE)
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
    void stashTextShowsTheStashItsBranchWhenItWasStashedADividerAndTheChanges() {
        Stash stash =
                new Stash("stash@{0}", new Sha("5555"), "my work", Option.of(new BranchName("main")), Instant.EPOCH);

        List<String> lines = ContentPanel.stashText(
                        stash,
                        ContentView.shown(DiffText.of(" a.txt | 1 +\n\n+added line\n")),
                        PLAIN_LOOK,
                        Theme.SpanStyles.NONE)
                .lines()
                .stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals("stash@{0}: my work", lines.get(0));
        assertEquals("Branch: main", lines.get(1));
        assertTrue(lines.get(2).startsWith("Date: "), lines.get(2));
        assertEquals(List.of("---", " a.txt | 1 +", "", "+added line"), lines.drop(3));
    }

    @Test
    void commitTextHasNoBlankLineWithoutAnExtendedMessage() {
        Commit commit =
                Fixtures.commit(new Sha("a1b2c3d4e5f6"), "Ada", "ada@example.com", Instant.EPOCH, "Subject", "");

        List<String> lines = ContentPanel.commitText(
                        commit, ContentView.shown(DiffText.of("x")), PLAIN_LOOK, Theme.SpanStyles.NONE)
                .lines()
                .stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals(List.of("", "    Subject", "---", "x"), lines.drop(3));
    }

    @Test
    void aTruncatedDiffEndsWithALineSayingSo() {
        assertEquals("+a\n", ContentView.shown(DiffText.of("+a\n")).text());
        assertEquals(
                "+a\n… truncated\n",
                ContentView.shown(DiffText.of("+a\n", true)).text());
    }

    @Test
    void logLinesShowTheExtendedMessageFollowedByABlankLine() {
        Commit commit = Fixtures.commit(
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
                Fixtures.commit(
                        new Sha("a1b2c3d"),
                        "Ada",
                        "ada@example.com",
                        Instant.parse("2026-10-09T12:30:00Z"),
                        "Second",
                        ""),
                Fixtures.commit(
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
