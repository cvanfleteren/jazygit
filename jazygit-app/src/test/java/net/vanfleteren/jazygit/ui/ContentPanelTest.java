package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.HashSet;
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
import net.vanfleteren.jazygit.git.Git;
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
    void onAMarkerEnterAndTheBracketsAskToShowTheLinesItLeavesOut() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel expanding = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model shown = showingCommit("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -9,2 +9,2 @@
                 nine
                -ten
                +TEN
                """)
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        String area = expanding.areaOfPane(shown, CommitsPanel.ID).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.Gap above = new Anchor.Gap("a.txt", 1, Option.of(9));
        Model onMarker = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(above), Option.none())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> expanding.render(onMarker, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> expanding.render(onMarker, area));
            assertTrue(screen.contains("8 lines hidden"), screen);
            assertTrue(screen.contains("more ┄"), screen);
            SettledPilot.focus(testRunner, area);
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).press(']');
            SettledPilot.of(testRunner).pause();
        }

        List<net.vanfleteren.jazygit.feature.diff.ExpandMsg.Asked> asked = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.feature.diff.ExpandMsg.Asked.class::isInstance)
                .map(net.vanfleteren.jazygit.feature.diff.ExpandMsg.Asked.class::cast);
        assertEquals(
                List.of(
                        net.vanfleteren.jazygit.feature.diff.ExpandMsg.Toward.ALL,
                        net.vanfleteren.jazygit.feature.diff.ExpandMsg.Toward.DOWN),
                asked.map(net.vanfleteren.jazygit.feature.diff.ExpandMsg.Asked::toward));
        assertEquals(List.of(above, above), asked.map(msg -> msg.at().cursor()));
        assertEquals(List.of(area, area), asked.map(msg -> msg.at().area()));
    }

    @Test
    void ctrlOShowsTheNewVersionOfTheFileInPlaceOfItsDiffOnceItIsRead() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel versions = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        String header = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
        Model shown = showingCommit(header + "@@ -9,2 +9,2 @@\n nine\n-ten\n+TEN\n")
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        String area = versions.areaOfPane(shown, CommitsPanel.ID).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine removed = new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.LEFT, 10);
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(removed), Option.none())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> versions.render(onLine, area))) {
            RenderedText.ofLarge(testRunner, () -> versions.render(onLine, area));
            SettledPilot.focus(testRunner, area);
            SettledPilot.of(testRunner).press('o', dev.tamboui.tui.event.KeyModifiers.CTRL);
            SettledPilot.of(testRunner).pause();
        }
        List<net.vanfleteren.jazygit.feature.diff.VersionMsg.Cycled> cycled = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.feature.diff.VersionMsg.Cycled.class::isInstance)
                .map(net.vanfleteren.jazygit.feature.diff.VersionMsg.Cycled.class::cast);
        assertEquals(List.of(removed), cycled.map(msg -> msg.at().cursor()));

        Update.Next asked = Update.update(onLine, cycled.head());
        ReadVersion read = (ReadVersion) asked.cmds().head();
        DiffText whole = DiffText.of(header + "@@ -8,4 +8,4 @@\n eight\n nine\n-ten\n+TEN\n eleven\n");
        Model version = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, whole))
                .model();

        ContentPanel drawing = new ContentPanel(Theme.SpanStyles.NONE, () -> false);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> drawing.render(version, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> drawing.render(version, area));
            assertTrue(screen.contains("new version  a.txt"), screen);
            assertTrue(screen.contains("11 │  eleven"), screen);
            assertTrue(screen.contains("10 │ +TEN"), screen);
            assertFalse(screen.contains("-ten"), screen);
        }
    }

    @Test
    void yAsksToCopyWhatTheCursorIsOn() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel copying = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model shown = showingCommit("""
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -9,2 +9,2 @@
                 nine
                -ten
                +TEN
                """)
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        String area = copying.areaOfPane(shown, CommitsPanel.ID).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine added = new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 10);
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(added), Option.none())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> copying.render(onLine, area))) {
            RenderedText.ofLarge(testRunner, () -> copying.render(onLine, area));
            SettledPilot.focus(testRunner, area);
            SettledPilot.of(testRunner).press('y');
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
        }

        List<net.vanfleteren.jazygit.feature.copy.CopyMsg.Asked> asked = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.feature.copy.CopyMsg.Asked.class::isInstance)
                .map(net.vanfleteren.jazygit.feature.copy.CopyMsg.Asked.class::cast);
        assertEquals(List.of(added), asked.map(msg -> msg.at().cursor()));
        assertEquals(List.of(area), asked.map(msg -> msg.at().area()));
        // v opens a range in the diff of a commit too, which y then copies.
        assertEquals(
                List.of(Option.of(added)),
                List.ofAll(dispatched)
                        .filter(Msg.ContentScrolled.class::isInstance)
                        .map(Msg.ContentScrolled.class::cast)
                        .map(scrolled -> scrolled.to().mark()));
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
    void hAndLScrollAFocusedAreaSidewaysAsTheArrowsDo() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(showingCommit("+0123456789" + "x".repeat(300)));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        String area = scrolling.areaOfPane(model.get(), CommitsPanel.ID).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.of('l', 'l', 'h').forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
            screen = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
        }

        assertEquals(
                List.of(1, 2, 1),
                List.ofAll(dispatched)
                        .map(msg -> ((Msg.ContentScrolled) msg).to().column()));
        // What shows is scrolled too, not only where the model has it.
        assertTrue(screen.contains("│0123456789x"), screen);
        assertFalse(screen.contains("+0123456789"), screen);
    }

    @Test
    void rightStopsWithTheEndOfTheWidestLineAtTheEdgeOfTheAreaBesideItsScrollbar() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(showingCommit("+" + "x".repeat(110) + "END"));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        String area = scrolling.areaOfPane(model.get(), CommitsPanel.ID).get();

        String screen;
        // The frames the keys draw are as large as the screen read at the end, which the area learns its width from.
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.range(0, 40).forEach(i -> SettledPilot.of(testRunner).press(KeyCode.RIGHT));
            SettledPilot.of(testRunner).pause();
            screen = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
        }

        List<Integer> columns = List.ofAll(dispatched)
                .map(msg -> ((Msg.ContentScrolled) msg).to().column());
        int last = columns.last();
        assertTrue(last > 0 && last < 40, columns.toString());
        assertEquals(List.fill(40 - columns.indexOf(last), last), columns.drop(columns.indexOf(last)));
        assertTrue(java.util.regex.Pattern.compile("xEND[█│]").matcher(screen).find(), screen);
    }

    @Test
    void allTheLinesThatShowAreScrolledSidewaysAlsoWhenTheAreaGrowsAtTheBottom() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(showingCommit(List.range(0, 200)
                        .map(i -> "+%03d".formatted(i) + "x".repeat(150))
                        .mkString("\n")));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        String area = scrolling.areaOfPane(model.get(), CommitsPanel.ID).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.END);
            List.range(0, 3).forEach(i -> SettledPilot.of(testRunner).press(KeyCode.RIGHT));
            SettledPilot.of(testRunner).pause();
            // On a taller screen, the area shows lines above those it showed.
            screen = RenderedText.ofTall(testRunner, () -> scrolling.render(model.get(), area));
        }

        // Each line shows without its first three columns: only the last digit of its number is left.
        assertTrue(screen.contains("│9xxx"), screen);
        assertFalse(
                java.util.regex.Pattern.compile("│\\+?\\d\\d").matcher(screen).find(), screen);
    }

    @Test
    void theLinesOfALongDiffShowAlsoWhenTheAreaGrowsAtTheBottom() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(showingCommit(
                        List.range(0, 2000).map(i -> "+line %04d".formatted(i)).mkString("\n")));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        String area = scrolling.areaOfPane(model.get(), CommitsPanel.ID).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.END);
            SettledPilot.of(testRunner).pause();
            // On a taller screen, the area shows lines above those it showed.
            screen = RenderedText.ofTall(testRunner, () -> scrolling.render(model.get(), area));
        }

        assertTrue(screen.contains("+line 1999"), screen);
        // No blank line in place of one that shows: each row up to the last line of the diff has one.
        List<String> rows = List.of(screen.split("\n"))
                .dropUntil(row -> row.contains("+line "))
                .takeWhile(row -> row.contains("+line "));
        assertTrue(rows.size() > 30, screen);
        assertTrue(rows.last().contains("+line 1999"), screen);
    }

    @Test
    void anAreaIsGivenAllTheLinesButDrawsAndMeasuresOnlyThoseThatShow() {
        java.util.concurrent.atomic.AtomicInteger drawn = new java.util.concurrent.atomic.AtomicInteger();
        Rows lines = Rows.drawn(io.vavr.collection.Vector.range(0, 100).map(i -> () -> {
            drawn.incrementAndGet();
            return Line.from("%03d".formatted(i));
        }));

        java.util.List<Line> windowed =
                ContentPanel.windowed(lines, 1, 40, 10, 0, HashSet.empty()).lines();

        assertEquals(100, windowed.size());
        assertEquals(20, drawn.get());
        // From where it is scrolled to, as many lines as an area twice as high shows, cut sideways.
        assertEquals("40", windowed.get(40).rawContent());
        assertEquals("59", windowed.get(59).rawContent());
        assertEquals("", windowed.get(39).rawContent());
        assertEquals("", windowed.get(60).rawContent());
        // At the end, the lines an area twice as high would show from its last line at its bottom.
        java.util.List<Line> atEnd =
                ContentPanel.windowed(lines, 0, 95, 10, 0, HashSet.empty()).lines();
        assertEquals("080", atEnd.get(80).rawContent());
        assertEquals("", atEnd.get(79).rawContent());
        // Before the area was ever drawn, none of them, though it still gets as many.
        int before = drawn.get();
        java.util.List<Line> undrawn =
                ContentPanel.windowed(lines, 0, 50, 0, 0, HashSet.empty()).lines();
        assertEquals(100, undrawn.size());
        assertEquals("", undrawn.get(50).rawContent());
        assertEquals(before, drawn.get());
    }

    @Test
    void aLineThatFillsTheAreaHasItsBlankLastSpanLengthenedToTheEdge() {
        Style green = Style.EMPTY.bg(Color.GREEN);
        Line changed = Line.from(Span.raw(" 1 │ "), Span.styled("+ab", green), Span.styled(" ", green));

        assertEquals(
                Line.from(Span.raw(" 1 │ "), Span.styled("+ab", green), Span.styled("      ", green)),
                ContentPanel.filled(changed, 14));
        // A line as wide already, and one that does not end in padding, are left as they are.
        assertEquals(changed, ContentPanel.filled(changed, 9));
        Line context = Line.from(Span.raw(" 1 │ "), Span.raw(" ab"));
        assertEquals(context, ContentPanel.filled(context, 14));
    }

    @Test
    void onlyTheLinesThatFillAreDrawnToTheEdgeAndThenScrolledSideways() {
        Style green = Style.EMPTY.bg(Color.GREEN);
        Rows lines = Rows.of(io.vavr.collection.Vector.of(
                Line.from(Span.raw("ab"), Span.styled(" ", green)),
                Line.from(Span.raw("ab"), Span.styled(" ", green))));

        java.util.List<Line> windowed =
                ContentPanel.windowed(lines, 1, 0, 10, 6, HashSet.of(0)).lines();

        // Filled to the edge of the area where it is scrolled to, then cut sideways.
        assertEquals(Line.from(Span.raw("b"), Span.styled("     ", green)), windowed.get(0));
        assertEquals(Line.from(Span.raw("b"), Span.styled(" ", green)), windowed.get(1));
    }

    @Test
    void theBackgroundOfAChangedLineGoesOnToTheEdgeOfTheArea() throws Exception {
        ContentPanel filling = new ContentPanel(GREEN_ADDED, () -> false, id -> {}, msg -> {});
        Model model = showingRichCommit("+short\n+a bit longer");
        String area = filling.areaOfPane(model, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> filling.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            dev.tamboui.buffer.Buffer buffer = drawn(testRunner, () -> filling.render(model, area), 100);
            // From its marker to the scrollbar, past the widest line of the file, not just as wide as that: from the
            // first frame, though the area only learns how wide it is while it draws.
            assertBackgroundTo(Color.GREEN, "+short", 97, buffer);
            // The bar of the file too.
            assertBackgroundTo(Color.BLUE, "added  schema.sql", 97, buffer);
        }
    }

    @Test
    void aBarWiderThanTheLinesOfItsFileGoesOnToTheEdgeOfTheArea() throws Exception {
        ContentPanel filling = new ContentPanel(GREEN_ADDED, () -> false, id -> {}, msg -> {});
        // The bar is the widest line of its file: it is not padded to the width of the file.
        Model model = showingRichCommit("+x");
        String area = filling.areaOfPane(model, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> filling.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            assertBackgroundTo(
                    Color.BLUE, "added  schema.sql", 97, drawn(testRunner, () -> filling.render(model, area), 100));
        }
    }

    @Test
    void theFirstFrameInAnotherSizeFillsTheAreaItIsDrawnIn() throws Exception {
        ContentPanel filling = new ContentPanel(GREEN_ADDED, () -> false, id -> {}, msg -> {});
        Model model = showingRichCommit("+short\n+a bit longer");
        String area = filling.areaOfPane(model, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> filling.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            drawn(testRunner, () -> filling.render(model, area), 60);
            // Wider, as when the left column is made narrower: on to the new edge, not that of the frame before.
            assertBackgroundTo(Color.GREEN, "+short", 97, drawn(testRunner, () -> filling.render(model, area), 100));
            // Narrower again: nothing left of the frame before, like a column for a scrollbar.
            assertBackgroundTo(Color.GREEN, "+short", 57, drawn(testRunner, () -> filling.render(model, area), 60));
        }
    }

    @Test
    void theStagedAndUnstagedChangesEachTakeHalfOfTheArea() throws Exception {
        Model selected = TestModels.withFilesSelected(
                SampleData.git(),
                List.of(
                        new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                        new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(selected, FilesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(selected, FilesPanel.ID));
            List<String> rows = List.of(screen.split("\n"));
            int staged = rows.indexWhere(row -> row.contains("Staged changes"));
            int unstaged = rows.indexWhere(row -> row.contains("Unstaged changes"));
            // Each takes half of the 30 rows, not only as many as its first lines take.
            assertEquals(15, unstaged - staged, screen);
            assertEquals(2, rows.count(row -> row.contains("+new")), screen);
        }
    }

    // The added lines in green, the bars of the files in blue.
    private static final Theme.SpanStyles GREEN_ADDED = cssClass -> switch (cssClass) {
        case Theme.Classes.DIFF_ADDED_LINE -> Style.EMPTY.bg(Color.GREEN);
        case Theme.Classes.DIFF_FILE_HEADER -> Style.EMPTY.bg(Color.BLUE);
        default -> Style.EMPTY;
    };

    /**
     * The model with a commit selected that adds {@code schema.sql} with {@code lines}, drawn rich.
     */
    private static Model showingRichCommit(String lines) {
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        int count = lines.split("\n").length;
        return Update.update(
                        Update.update(rich, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"),
                                rich.preferences().diffOptions(),
                                DiffText.of(String.join(
                                        "\n",
                                        "diff --git a/schema.sql b/schema.sql",
                                        "new file mode 100644",
                                        "--- /dev/null",
                                        "+++ b/schema.sql",
                                        "@@ -0,0 +1," + count + " @@",
                                        lines))))
                .model();
    }

    /**
     * The cells {@code element} draws on a screen {@code width} columns wide and 30 high, on the render thread.
     */
    private static dev.tamboui.buffer.Buffer drawn(
            ToolkitTestRunner testRunner,
            java.util.function.Supplier<? extends dev.tamboui.toolkit.element.Element> element,
            int width)
            throws Exception {
        dev.tamboui.layout.Rect screen = new dev.tamboui.layout.Rect(0, 0, width, 30);
        java.util.concurrent.CompletableFuture<dev.tamboui.buffer.Buffer> drawn =
                new java.util.concurrent.CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> {
            dev.tamboui.buffer.Buffer buffer = dev.tamboui.buffer.Buffer.empty(screen);
            element.get()
                    .render(
                            dev.tamboui.terminal.Frame.forTesting(buffer),
                            screen,
                            dev.tamboui.toolkit.element.RenderContext.empty());
            drawn.complete(buffer);
        });
        return drawn.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Asserts the background of the row of {@code buffer} that shows {@code text} is {@code colour} in one stretch, up
     * to column {@code last}.
     */
    private static void assertBackgroundTo(Color colour, String text, int last, dev.tamboui.buffer.Buffer buffer) {
        int width = buffer.area().width();
        int row = List.range(0, buffer.area().height())
                .find(y -> List.range(0, width)
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString()
                        .contains(text))
                .get();
        List<Integer> coloured =
                List.range(0, width).filter(x -> buffer.get(x, row).style().bg().equals(java.util.Optional.of(colour)));
        assertEquals(last, coloured.last(), coloured.toString());
        assertEquals(coloured.last() - coloured.head() + 1, coloured.size(), coloured.toString());
    }

    @Test
    void sidewaysCutsTheFirstColumnsOffALineKeepingTheStylesOfItsSpans() {
        Style red = Style.EMPTY.fg(Color.RED);
        Style blue = Style.EMPTY.fg(Color.BLUE);
        Style green = Style.EMPTY.fg(Color.GREEN);
        Line line = Line.from(Span.styled("ab", red), Span.styled("cd", blue), Span.styled("漢e", green));

        assertEquals(line, ContentPanel.sideways(line, 0));
        assertEquals(
                Line.from(Span.styled("b", red), Span.styled("cd", blue), Span.styled("漢e", green)),
                ContentPanel.sideways(line, 1));
        assertEquals(Line.from(Span.styled("cd", blue), Span.styled("漢e", green)), ContentPanel.sideways(line, 2));
        assertEquals(Line.from(Span.styled("d", blue), Span.styled("漢e", green)), ContentPanel.sideways(line, 3));
        // Through the wide character: a space for its column past the cut, in the style of its span.
        assertEquals(Line.from(Span.styled(" e", green)), ContentPanel.sideways(line, 5));
        assertEquals(Line.from(Span.styled("e", green)), ContentPanel.sideways(line, 6));
        assertEquals(Line.from(java.util.List.of()), ContentPanel.sideways(line, 9));
    }

    @Test
    void leftLeavesADiffOnceItIsScrolledAllTheWayLeft() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(TestModels.withFilesSelected(
                        SampleData.git(),
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
            // h scrolls, but does not leave as Left does.
            SettledPilot.of(testRunner).press('h');
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
        assertEquals(
                List.of(1, 0, 0),
                List.ofAll(dispatched)
                        .map(msg -> ((Msg.ContentScrolled) msg).to().column()));
    }

    @Test
    void leftLeavesTheAreasOfThePanesBesidesTheFilesToo() throws Exception {
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

        assertEquals(1, leaves.get());
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
    void aLongRichDiffIsMeasuredOnceAndThenOnlyTheLinesThatShowAreDrawn() throws Exception {
        // Each line of the diff resolves the class of its gutter once each time it is drawn.
        java.util.concurrent.atomic.AtomicInteger gutters = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel counting = new ContentPanel(
                cssClass -> {
                    if (cssClass.equals(Theme.Classes.DIFF_LINE_NUMBER)) {
                        gutters.incrementAndGet();
                    }
                    return dev.tamboui.style.Style.EMPTY;
                },
                () -> false,
                id -> {},
                dispatched::add);
        int lines = 2_000;
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        Model model = Update.update(
                        Update.update(rich, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"),
                                rich.preferences().diffOptions(),
                                DiffText.of("diff --git a/a.txt b/a.txt\n--- /dev/null\n+++ b/a.txt\n@@ -0,0 +1,"
                                        + lines + " @@\n"
                                        + List.range(0, lines)
                                                .map(i -> "+line " + i)
                                                .mkString("\n") + "\n")))
                .model();
        String area = counting.areaOfPane(model, CommitsPanel.ID).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> counting.render(model, area))) {
            SettledPilot.focus(testRunner, area);
            String screen = RenderedText.ofLarge(testRunner, () -> counting.render(model, area));
            assertTrue(screen.contains("+line 0"), screen);
            // Measured once, all of them; drawn, only those of an area up to twice as high as the screen: not all of
            // them again for the frame the area is first drawn in only to learn its size.
            int shown = gutters.get();
            assertTrue(shown >= lines && shown <= lines + 2 * 30, "drew " + shown);

            // In the size the runner draws in, which can show more lines than the screen above.
            SettledPilot.of(testRunner).pause();
            int settled = gutters.get();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            Msg.ContentScrolled down = (Msg.ContentScrolled) dispatched.getLast();
            assertEquals(Option.of(new Anchor.File("a.txt")), down.to().cursor());
            assertEquals(0, down.to().row());
            // The cursor moved over lines kept drawn: none is drawn again.
            assertEquals(settled, gutters.get());
        }
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
    void aClickOnTheLogGivesItTheFocus() throws Exception {
        java.util.List<String> focused = new java.util.ArrayList<>();
        ContentPanel clicking = new ContentPanel(Theme.SpanStyles.NONE, () -> false, focused::add, msg -> {});
        Model model = TestModels.loaded(SampleData.git(), "feature/initial-layout");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> clicking.render(model, BranchesPanel.ID))) {
            RenderedText.of(testRunner, () -> clicking.render(model, BranchesPanel.ID));
            dev.tamboui.layout.Rect drawn = SettledPilot.of(testRunner).findElement("branch-log");

            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, drawn.y() + 2);
        }

        assertEquals(java.util.List.of("branch-log"), focused);
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
    void inTheDiffOfAPullRequestYCopiesAndVStaysTheKeyOfTheReview() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel reviewing = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model model = pullRequestDiff();
        String area = reviewing.areaOfPane(model, FilesPanel.ID).get();
        Anchor.OnLine line = new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 1);

        java.util.concurrent.atomic.AtomicReference<Model> drawn =
                new java.util.concurrent.atomic.AtomicReference<>(model);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(drawn.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            drawn.set(Update.update(model, dispatched.getLast()).model());
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));

            // On a thread, v is the review's: it says why no range opens, where the key of every rich diff says
            // nothing.
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    new net.vanfleteren.jazygit.feature.github.review.ReviewMsg.Refused(
                            net.vanfleteren.jazygit.state.AppError.CannotReview.Why.NOT_ON_LINE),
                    dispatched.getLast());

            SettledPilot.of(testRunner).press('k');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(drawn.get(), area));
            SettledPilot.of(testRunner).press('y');
            SettledPilot.of(testRunner).pause();
        }

        List<net.vanfleteren.jazygit.feature.copy.CopyMsg.Asked> asked = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.feature.copy.CopyMsg.Asked.class::isInstance)
                .map(net.vanfleteren.jazygit.feature.copy.CopyMsg.Asked.class::cast);
        assertEquals(List.of(line), asked.map(msg -> msg.at().cursor()));
        assertEquals(List.of(area), asked.map(msg -> msg.at().area()));
    }

    @Test
    void lScrollsTheDiffOfAPullRequestSidewaysWithItsCursorOnAThreadToAWideSuggestion() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                pullRequestDiff("Wrap it:\n\n```suggestion\n" + "0123456789" + "x".repeat(300) + "\n```"));
        ContentPanel reviewing = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        String area = reviewing.areaOfPane(model.get(), FilesPanel.ID).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('l');
            SettledPilot.of(testRunner).pause();
            screen = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
        }

        assertEquals(1, ((Msg.ContentScrolled) dispatched.getLast()).to().column(), dispatched.toString());
        // The whole diff shows a column further, its bar too, which starts with a blank.
        assertTrue(screen.contains("│modified  a.txt"), screen);
        assertTrue(screen.contains("│0123456789x"), screen);
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

    @Test
    void aRangeShowsItsHunkAsGitGivesItWithTheLineOfTheCursorWhereItWas() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                pullRequestDiff("Why?", """
                        diff --git a/a.txt b/a.txt
                        --- a/a.txt
                        +++ b/a.txt
                        @@ -1,42 +1,42 @@
                        """ + " context\n".repeat(40) + """
                                -int count = 1;
                                -int total = 2;
                                +int count = 42;
                                +int total = 43;
                                """));
        ContentPanel reviewing = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        String area = reviewing.areaOfPane(model.get(), FilesPanel.ID).get();

        String paired;
        String ranged;
        String dropped;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reviewing.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area);
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            // To the end of the diff, scrolled down, and up from its marker to the second removed line.
            SettledPilot.of(testRunner).press(KeyCode.END);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            for (int step = 0; step < 2; step++) {
                SettledPilot.of(testRunner).press('k');
                SettledPilot.of(testRunner).pause();
                RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            }
            paired = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            ranged = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
            dropped = RenderedText.ofLarge(testRunner, () -> reviewing.render(model.get(), area));
        }

        assertTrue(
                row(paired, "-int count") < row(paired, "+int count")
                        && row(paired, "+int count") < row(paired, "-int total"),
                paired);
        assertTrue(
                row(ranged, "-int count") < row(ranged, "-int total")
                        && row(ranged, "-int total") < row(ranged, "+int count"),
                ranged);
        assertEquals(row(paired, "-int total"), row(ranged, "-int total"), paired + ranged);
        assertEquals(row(paired, "-int total"), row(dropped, "-int total"), paired + dropped);
        assertTrue(row(dropped, "+int count") < row(dropped, "-int total"), dropped);
    }

    /**
     * The row of {@code screen} that shows {@code text}.
     */
    private static int row(String screen, String text) {
        return List.of(screen.split("\n")).indexWhere(line -> line.contains(text));
    }

    /**
     * A pull request's files open in the files pane, in the rich view, its one file selected: a diff of one added
     * line, with a thread on it.
     */
    private static Model pullRequestDiff() {
        return pullRequestDiff("Why?");
    }

    /**
     * {@link #pullRequestDiff()}, the one comment of its thread saying {@code body}.
     */
    private static Model pullRequestDiff(String body) {
        return pullRequestDiff(body, """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -0,0 +1 @@
                +a
                """);
    }

    /**
     * {@link #pullRequestDiff(String)}, of the diff {@code diff}, with the thread on the first line of the new file.
     */
    private static Model pullRequestDiff(String body, String diff) {
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
                        "bob",
                        Instant.EPOCH,
                        body,
                        Option.none(),
                        "C",
                        "1",
                        net.vanfleteren.jazygit.gh.model.CommentRights.ALL)),
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
                                        List.of(thread)),
                                HashSet.empty(),
                                0))
                .model();
        return Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), rich.preferences().diffOptions(), DiffText.of(diff)))
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
        assertTrue(lines.contains("* | commit b"));
        assertTrue(lines.contains("|/  Ada <a@e.com>"));
        assertTrue(lines.contains("* commit a"));
    }

    @Test
    void logLinesShowTheParentsOfAMergeWhoseOtherParentIsNotListed() {
        List<String> lines =
                ContentPanel.logLines(List.of(merge("f2", "f1", "mm"), plain("f1", "base")), ZoneOffset.UTC);

        assertEquals("| Merge: f1 mm", lines.get(lines.indexOf("* commit f2") + 1));
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
        Git data = SampleData.git();
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
