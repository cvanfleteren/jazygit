package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The diff of a file in the content panel: the lines it leaves out, the version of the file in place of it, plain,
 * rendered or blamed, and what {@code y} copies there.
 */
class FileDiffContentTest {

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    @Test
    void onAMarkerEnterAndTheBracketsAskToShowTheLinesItLeavesOut() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel expanding = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model shown = ContentPanelFixtures.showingCommit("""
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
        AreaId area = expanding.areaOfPane(shown, CommitsPanel.PANE).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.Gap above = new Anchor.Gap("a.txt", 1, Option.of(9));
        Model onMarker = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(above), Option.none())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> expanding.render(onMarker, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> expanding.render(onMarker, area));
            assertTrue(screen.contains("8 lines hidden"), screen);
            assertTrue(screen.contains("more ┄"), screen);
            SettledPilot.focus(testRunner, area.id());
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).press(']');
            SettledPilot.of(testRunner).pause();
        }

        List<net.vanfleteren.jazygit.features.changes.diff.ExpandMsg.Asked> asked = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.features.changes.diff.ExpandMsg.Asked.class::isInstance)
                .map(net.vanfleteren.jazygit.features.changes.diff.ExpandMsg.Asked.class::cast);
        assertEquals(
                List.of(
                        net.vanfleteren.jazygit.features.changes.diff.ExpandMsg.Toward.ALL,
                        net.vanfleteren.jazygit.features.changes.diff.ExpandMsg.Toward.DOWN),
                asked.map(net.vanfleteren.jazygit.features.changes.diff.ExpandMsg.Asked::toward));
        assertEquals(List.of(above, above), asked.map(msg -> msg.at().cursor()));
        assertEquals(List.of(area, area), asked.map(msg -> msg.at().area()));
    }

    @Test
    void ctrlOShowsTheNewVersionOfTheFileInPlaceOfItsDiffOnceItIsRead() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel versions = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        String header = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
        Model shown = ContentPanelFixtures.showingCommit(header + "@@ -9,2 +9,2 @@\n nine\n-ten\n+TEN\n")
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        AreaId area = versions.areaOfPane(shown, CommitsPanel.PANE).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine removed =
                new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.LEFT, 10);
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(removed), Option.none())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> versions.render(onLine, area))) {
            RenderedText.ofLarge(testRunner, () -> versions.render(onLine, area));
            SettledPilot.focus(testRunner, area.id());
            SettledPilot.of(testRunner).press('o', dev.tamboui.tui.event.KeyModifiers.CTRL);
            SettledPilot.of(testRunner).pause();
        }
        List<net.vanfleteren.jazygit.features.changes.diff.VersionMsg.Cycled> cycled = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.features.changes.diff.VersionMsg.Cycled.class::isInstance)
                .map(net.vanfleteren.jazygit.features.changes.diff.VersionMsg.Cycled.class::cast);
        assertEquals(List.of(removed), cycled.map(msg -> msg.at().cursor()));

        Update.Next asked = Update.update(onLine, cycled.head());
        ReadVersion read = (ReadVersion) asked.cmds().head();
        ContentPanel reading = new ContentPanel(Theme.SpanStyles.NONE, () -> false);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> reading.render(asked.model(), area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> reading.render(asked.model(), area));
            // The title says what is coming while the diff still shows.
            assertTrue(screen.contains("· new version (reading)"), screen);
            assertTrue(screen.contains("- ten"), screen);
        }
        DiffText whole = DiffText.of(header + "@@ -8,4 +8,4 @@\n eight\n nine\n-ten\n+TEN\n eleven\n");
        Model version = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, whole))
                .model();

        ContentPanel drawing = new ContentPanel(Theme.SpanStyles.NONE, () -> false);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> drawing.render(version, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> drawing.render(version, area));
            assertTrue(screen.contains("new version  a.txt"), screen);
            assertTrue(screen.contains("· new version"), screen);
            assertFalse(screen.contains("(reading)"), screen);
            assertTrue(screen.contains("11 │   eleven"), screen);
            assertTrue(screen.contains("10 │ + TEN"), screen);
            assertFalse(screen.contains("- ten"), screen);
        }
    }

    @Test
    void aVersionLongerThanTheDiffIsDrawnAtOnceAtItsCursor() throws Exception {
        List<List<String>> screens = beforeAndAfterTheVersion();

        assertTrue(
                screens.last().exists(row -> row.contains(" 39 │   line39")),
                screens.last().mkString("\n"));
    }

    @Test
    void theLineOfTheCursorStaysWhereItWasOnTheScreenAsTheVersionShows() throws Exception {
        List<List<String>> screens = beforeAndAfterTheVersion();

        int was = screens.head().indexWhere(row -> row.contains(" 39 │   line39"));
        assertTrue(was > 0, screens.head().mkString("\n"));
        assertEquals(
                was,
                screens.last().indexWhere(row -> row.contains(" 39 │   line39")),
                screens.last().mkString("\n"));
    }

    @Test
    void renderedTheBlockAfterABlankLineOfTheCursorTakesItsRowOnTheScreen() throws Exception {
        String header = "diff --git a/a.md b/a.md\n--- a/a.md\n+++ b/a.md\n";
        // Paragraphs on the odd lines, blank lines between them; the one on line 21 changed.
        java.util.function.IntFunction<String> line =
                n -> n == 21 ? "-para 21\n+changed 21\n" : n % 2 == 1 ? " para " + n + "\n" : " \n";
        Model shown = ContentPanelFixtures.showingCommit(header + "@@ -18,7 +18,7 @@\n"
                        + List.rangeClosed(18, 24).map(line::apply).mkString())
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false);
        AreaId area = panel.areaOfPane(shown, CommitsPanel.PANE).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine blank =
                new Anchor.OnLine("a.md", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT, 20);
        Model onBlank = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(blank), Option.none())));
        DiffCursorAt at = new DiffCursorAt(
                area, onBlank.scrolled(area, content), blank, Option.none(), Option.none(), Option.none());
        Update.Next asked =
                Update.update(onBlank, new net.vanfleteren.jazygit.features.changes.diff.VersionMsg.RenderToggled(at));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        DiffText whole = DiffText.of(header + "@@ -1,41 +1,41 @@\n"
                + List.rangeClosed(1, 41).map(line::apply).mkString());
        Model rendered = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, whole))
                .model();
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(onBlank);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model.get(), area))) {
            List<String> before = List.of(RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), area))
                    .split("\n"));
            model.set(rendered);
            List<String> after = List.of(RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), area))
                    .split("\n"));

            int was = before.indexWhere(row -> row.contains(" 20 │"));
            assertTrue(was > 0, before.mkString("\n"));
            assertEquals(was, after.indexWhere(row -> row.contains("changed 21")), after.mkString("\n"));
        }
    }

    @Test
    void ctrlTShowsTheNewVersionRenderedAndActsOnTheBlockAtTheTop() throws Exception {
        String header = "diff --git a/a.md b/a.md\n--- a/a.md\n+++ b/a.md\n";
        DiffText changes = DiffText.of(header + "@@ -1,3 +1,3 @@\n # Title\n-old text\n+new text\n \n");
        Model shown = ContentPanelFixtures.showingCommit(changes.text())
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        AreaId area = panel.areaOfPane(shown, CommitsPanel.PANE).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine removed =
                new Anchor.OnLine("a.md", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.LEFT, 2);
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(removed), Option.none())));
        DiffCursorAt at = new DiffCursorAt(
                area, onLine.scrolled(area, content), removed, Option.none(), Option.none(), Option.none());
        Update.Next asked =
                Update.update(onLine, new net.vanfleteren.jazygit.features.changes.diff.VersionMsg.RenderToggled(at));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        Model rendered = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, changes))
                .model();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(rendered, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(rendered, area));
            assertTrue(screen.contains("· new version, rendered"), screen);
            assertTrue(screen.contains("new version  a.md"), screen);
            assertTrue(screen.contains("+ new text"), screen);
            assertFalse(screen.contains("old text"), screen);
            assertFalse(screen.contains(" 2 │"), screen);
            SettledPilot.focus(testRunner, area.id());
            SettledPilot.of(testRunner).press('t', dev.tamboui.tui.event.KeyModifiers.CTRL);
            SettledPilot.of(testRunner).pause();
        }
        List<net.vanfleteren.jazygit.features.changes.diff.VersionMsg.RenderToggled> toggled = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.features.changes.diff.VersionMsg.RenderToggled.class::isInstance)
                .map(net.vanfleteren.jazygit.features.changes.diff.VersionMsg.RenderToggled.class::cast);
        // With the bar of the file at the top, the first block, its first line.
        assertEquals(
                List.of(new Anchor.OnLine("a.md", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT, 1)),
                toggled.map(msg -> msg.at().cursor()));
    }

    /**
     * The screen of a panel with the cursor on line 39 of a diff around line 41, and then, in the same panel, once the
     * new version of the file of 60 lines is read in place of it.
     */
    private static List<List<String>> beforeAndAfterTheVersion() throws Exception {
        String header = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
        java.util.function.IntFunction<String> line = n -> n == 41 ? "-line41\n+changed\n" : " line" + n + "\n";
        Model shown = ContentPanelFixtures.showingCommit(header + "@@ -38,7 +38,7 @@\n"
                        + List.rangeClosed(38, 44).map(line::apply).mkString())
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false);
        AreaId area = panel.areaOfPane(shown, CommitsPanel.PANE).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine on =
                new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT, 39);
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(on), Option.none())));
        DiffCursorAt at =
                new DiffCursorAt(area, onLine.scrolled(area, content), on, Option.none(), Option.none(), Option.none());
        Update.Next asked =
                Update.update(onLine, new net.vanfleteren.jazygit.features.changes.diff.VersionMsg.Cycled(at));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        DiffText whole = DiffText.of(header + "@@ -1,60 +1,60 @@\n"
                + List.rangeClosed(1, 60).map(line::apply).mkString());
        Model version = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, whole))
                .model();
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(onLine);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model.get(), area))) {
            List<String> before = List.of(RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), area))
                    .split("\n"));
            model.set(version);
            List<String> after = List.of(RenderedText.ofLarge(testRunner, () -> panel.render(model.get(), area))
                    .split("\n"));

            assertTrue(after.exists(row -> row.contains("· new version")), after.mkString("\n"));
            return List.of(before, after);
        }
    }

    @Test
    void enterOpensTheCommitOfALineOfABlamedVersionOnlyWhenItIsBlamedOnAnotherCommit() throws Exception {
        Model blamed = blamedVersion("");
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        AreaId area = content.areaOfPane(blamed, CommitsPanel.PANE).get();
        ScrolledContent shown = ContentView.of(blamed, area).areas().head().content();

        // Line 9 is blamed on another commit, line 10 on the commit shown; the bar of the file is no line.
        for (Anchor on : List.<Anchor>of(onNew(9), onNew(10), new Anchor.File("a.txt"))) {
            ContentPanel opening = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
            Model onLine = blamed.withScrolled(
                    io.vavr.collection.HashMap.of(area, new Scrolled(shown, 0, 0, Option.of(on), Option.none())));
            try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> opening.render(onLine, area))) {
                RenderedText.ofLarge(testRunner, () -> opening.render(onLine, area));
                SettledPilot.focus(testRunner, area.id());
                SettledPilot.of(testRunner).press(KeyCode.ENTER);
                SettledPilot.of(testRunner).pause();
            }
        }

        List<net.vanfleteren.jazygit.features.changes.diff.VersionMsg.LineOpened> opened = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.features.changes.diff.VersionMsg.LineOpened.class::isInstance)
                .map(net.vanfleteren.jazygit.features.changes.diff.VersionMsg.LineOpened.class::cast);
        assertEquals(List.of(onNew(9)), opened.map(msg -> msg.at().cursor()));
    }

    @Test
    void theGutterOfABlamedVersionStaysWhereItIsWhenTheCodeScrollsSideways() throws Exception {
        Model blamed = blamedVersion("wide ".repeat(40) + "END");
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>();
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        model.set(blamed);
        AreaId area = scrolling.areaOfPane(blamed, CommitsPanel.PANE).get();

        String before;
        String scrolled;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(140, 30))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
            before = RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
            SettledPilot.of(testRunner).press('L');
            SettledPilot.of(testRunner).pause();
            scrolled = RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
        }

        String line = before.split("\n")[ContentPanelFixtures.row(before, "line1 ")];
        String moved = scrolled.split("\n")[ContentPanelFixtures.row(before, "line1 ")];
        // The blame and the number stay; the code after them scrolled away.
        String gutter = line.substring(0, line.indexOf(" │ ") + 2);
        assertTrue(gutter.contains("bbbbbbb"), line);
        assertTrue(moved.startsWith(gutter), moved);
        assertFalse(moved.contains("line1 "), moved);
        assertTrue(ContentPanelFixtures.row(before, "END") < 0, before);
        assertTrue(ContentPanelFixtures.row(scrolled, "END") >= 0, scrolled);
    }

    @Test
    void aSearchInABlamedVersionScrollsItsCodePastTheGutterItKeeps() throws Exception {
        Model blamed = blamedVersion("wide ".repeat(40) + "END");
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>();
        ContentPanel searching = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        model.set(blamed);
        AreaId area = searching.areaOfPane(blamed, CommitsPanel.PANE).get();
        java.util.function.Supplier<dev.tamboui.toolkit.element.StyledElement<?>> render =
                () -> searching.render(model.get(), area);

        String right;
        String left;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(render::get, new dev.tamboui.layout.Size(140, 30))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofSize(testRunner, render, 140, 30);
            right = searched(testRunner, render, "/END");
            // Escape clears the search kept, so / starts another.
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
            left = searched(testRunner, render, "/line1");
        }

        // The match far to the right shows, the gutter still at the start of its row.
        String end = right.split("\n")[ContentPanelFixtures.row(right, "wide END")];
        assertTrue(end.contains("bbbbbbb") && end.indexOf("bbbbbbb") < end.indexOf("END"), right);
        // Searched back to the left, the code shows from its start again, right after the gutter.
        assertTrue(ContentPanelFixtures.row(left, " │ line1 wide") >= 0, left);
    }

    /**
     * The screen once {@code keys} are typed into the area and Enter is pressed, each key drawn as the app would.
     */
    private static String searched(
            ToolkitTestRunner testRunner,
            java.util.function.Supplier<dev.tamboui.toolkit.element.StyledElement<?>> render,
            String keys)
            throws Exception {
        for (char key : keys.toCharArray()) {
            SettledPilot.of(testRunner).press(key);
            SettledPilot.of(testRunner).pause();
            RenderedText.ofSize(testRunner, render, 140, 30);
        }
        SettledPilot.of(testRunner).press(KeyCode.ENTER);
        SettledPilot.of(testRunner).pause();
        RenderedText.ofSize(testRunner, render, 140, 30);
        return RenderedText.ofSize(testRunner, render, 140, 30);
    }

    private static Anchor.OnLine onNew(int number) {
        return new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT, number);
    }

    /**
     * The new version of a file of 10 lines, of the commit shown, blamed: lines 1 to 9 on another commit, line 10, which
     * it changed, on it. Line 1 ends in {@code tail}.
     */
    private static Model blamedVersion(String tail) {
        String header = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
        java.util.function.IntFunction<String> line =
                n -> n == 10 ? "-line10\n+changed\n" : " line" + n + (n == 1 ? " " + tail : "") + "\n";
        Model shown = ContentPanelFixtures.showingCommit(header + "@@ -9,2 +9,2 @@\n" + line.apply(9) + line.apply(10))
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        AreaId area = new ContentPanel(Theme.SpanStyles.NONE, () -> false)
                .areaOfPane(shown, CommitsPanel.PANE)
                .get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(onNew(9)), Option.none())));
        DiffCursorAt at = new DiffCursorAt(
                area, onLine.scrolled(area, content), onNew(9), Option.none(), Option.none(), Option.none());
        Update.Next asked =
                Update.update(onLine, new net.vanfleteren.jazygit.features.changes.diff.VersionMsg.Cycled(at));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        DiffText whole = DiffText.of(header + "@@ -1,10 +1,10 @@\n"
                + List.rangeClosed(1, 10).map(line::apply).mkString());
        Model version = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, whole))
                .model();
        Update.Next blaming =
                Update.update(version, new net.vanfleteren.jazygit.features.changes.diff.VersionMsg.BlameToggled(at));
        ReadBlame blame =
                (ReadBlame) blaming.cmds().filter(ReadBlame.class::isInstance).head();
        Blame.Commit other = new Blame.Commit(new Sha("b".repeat(40)), "Alex Moreau", Instant.EPOCH, "older", false);
        Blame.Commit own = new Blame.Commit(new Sha("aaaa"), "Alex Moreau", Instant.EPOCH, "this", false);
        Blame lines = new Blame(io.vavr.collection.Vector.rangeClosed(1, 10)
                .map(n -> new Blame.Line(n == 10 ? own : other, n, "a.txt", Option.none())));
        return TestModels.done(TestModels.running(blaming.model(), blame), blame, new LoadMsg.BlameRead(blame, lines))
                .model();
    }

    @Test
    void yAsksToCopyWhatTheCursorIsOn() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel copying = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model shown = ContentPanelFixtures.showingCommit("""
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
        AreaId area = copying.areaOfPane(shown, CommitsPanel.PANE).get();
        ScrolledContent content = ContentView.of(shown, area).areas().head().content();
        Anchor.OnLine added =
                new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side.RIGHT, 10);
        Model onLine = shown.withScrolled(
                io.vavr.collection.HashMap.of(area, new Scrolled(content, 0, 0, Option.of(added), Option.none())));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> copying.render(onLine, area))) {
            RenderedText.ofLarge(testRunner, () -> copying.render(onLine, area));
            SettledPilot.focus(testRunner, area.id());
            SettledPilot.of(testRunner).press('y');
            SettledPilot.of(testRunner).press('v');
            SettledPilot.of(testRunner).pause();
        }

        List<net.vanfleteren.jazygit.features.changes.copy.CopyMsg.Asked> asked = List.ofAll(dispatched)
                .filter(net.vanfleteren.jazygit.features.changes.copy.CopyMsg.Asked.class::isInstance)
                .map(net.vanfleteren.jazygit.features.changes.copy.CopyMsg.Asked.class::cast);
        assertEquals(List.of(added), asked.map(msg -> msg.at().cursor()));
        assertEquals(List.of(area), asked.map(msg -> msg.at().area()));
        // v opens a range in the diff of a commit too, which y then copies.
        assertEquals(
                List.of(Option.of(added)),
                List.ofAll(dispatched)
                        .filter(ViewMsg.ContentScrolled.class::isInstance)
                        .map(ViewMsg.ContentScrolled.class::cast)
                        .map(scrolled -> scrolled.to().mark()));
    }
}
