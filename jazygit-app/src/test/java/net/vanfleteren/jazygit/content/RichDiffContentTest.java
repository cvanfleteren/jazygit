package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The rich diff in the content panel: the split view, the cursor the keys and clicks move, and how little of a long
 * diff it draws.
 */
class RichDiffContentTest {

    @Test
    void theSplitViewShowsBothFilesSideBySideItsCursorOnASideAndScrollsTheirCodeKeepingTheGutters() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(showingSplitCommit(
                        " same\n-int count = 1;\n+int count = 42; // " + "the answer ".repeat(10) + "\n same again"));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String before;
        String scrolled;
        // Wide enough for the split view, which the keys act on as it is drawn.
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(140, 30))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
            before = RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
            List.of('j', 'j', 'j', '[', ']')
                    .forEach(key -> SettledPilot.of(testRunner).press(key));
            List.of('l', 'l', 'l', 'l')
                    .forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
            scrolled = RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
        }

        int row = ContentPanelFixtures.row(before, "int count = 1;");
        String[] lines = before.split("\n");
        // The removed line on the left, the added one beside it on the right, each numbered in its own file.
        assertTrue(lines[row].contains("2 │ - int count = 1;"), before);
        assertTrue(lines[row].contains("│2 │ + int count = 42;"), before);
        assertTrue(
                List.ofAll(dispatched)
                        .filter(ViewMsg.ContentScrolled.class::isInstance)
                        .map(msg -> ((ViewMsg.ContentScrolled) msg).to().cursor())
                        .containsAll(List.of(
                                Option.of(new Anchor.OnLine("schema.sql", ReviewThread.Side.LEFT, 2)),
                                Option.of(new Anchor.OnLine("schema.sql", ReviewThread.Side.RIGHT, 2)))),
                List.ofAll(dispatched)
                        .filter(ViewMsg.ContentScrolled.class::isInstance)
                        .map(msg -> ((ViewMsg.ContentScrolled) msg).to().cursor())
                        .toString());
        // Scrolled sideways, each half keeps its gutter, and marks the code cut off before it.
        String[] after = scrolled.split("\n");
        assertTrue(after[row].contains("2 │ - ‹count = 1;"), scrolled);
        assertTrue(after[row].contains("│2 │ + ‹count = 42;"), scrolled);
    }

    @Test
    void aFileTheSplitViewDrawsUnifiedScrollsSidewaysAsInTheUnifiedView() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(showingSplitCommit(String.join(
                        "\n",
                        " same",
                        "-int count = 1;",
                        "+int count = 42;",
                        " same again",
                        // A conflicted file, whose hunk has no numbers to pair its lines by.
                        "diff --cc f.txt",
                        "--- a/f.txt",
                        "+++ b/f.txt",
                        "@@@ -1,2 -1,2 +1,3 @@@",
                        "  a",
                        "++" + "wide ".repeat(40) + "END")));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

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

        assertTrue(ContentPanelFixtures.row(before, "END") < 0, before);
        // The end of the long line of the conflicted file shows, as it would in the unified view.
        assertTrue(ContentPanelFixtures.row(scrolled, "END") >= 0, scrolled);
        // The bar of the file drawn split stays where it is.
        assertEquals(
                before.split("\n")[ContentPanelFixtures.row(before, "schema.sql")],
                scrolled.split("\n")[ContentPanelFixtures.row(before, "schema.sql")]);
    }

    @Test
    void theCursorOfTheSplitViewKeepsItsSideOverTheBarOfAFileAndStartsOnTheNewSideInAnotherDiff() throws Exception {
        String twoFiles = String.join(
                "\n",
                "diff --git a/a.sql b/a.sql",
                "--- a/a.sql",
                "+++ b/a.sql",
                "@@ -1,2 +1,2 @@",
                "-old a",
                "+new a",
                " same a",
                "diff --git a/b.sql b/b.sql",
                "--- a/b.sql",
                "+++ b/b.sql",
                "@@ -1,2 +1,2 @@",
                "-old b",
                "+new b",
                " same b");
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(splitCommit(
                        Update.update(
                                        TestModels.loaded()
                                                .mapPreferences(preferences -> preferences.withSettings(
                                                        preferences.settings().withDiffStyle(DiffStyle.SPLIT))),
                                        TestModels.commitsLoaded(TestModels.COMMITS.append(Fixtures.commit(
                                                new Sha("bbbb"),
                                                "Ada",
                                                "ada@example.com",
                                                Instant.EPOCH,
                                                "Second",
                                                ""))))
                                .model(),
                        "aaaa",
                        twoFiles));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        List<Option<Anchor>> kept;
        List<Option<Anchor>> anew;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(140, 30))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
            // To the first file's added line, the removed one beside it, then down past the bar of the second file.
            List.of('j', 'j', '[', 'j', 'j', 'j', 'j')
                    .forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
            kept = cursors(dispatched);
            // Another commit, with another diff.
            model.set(splitCommit(model.get(), "bbbb", twoFiles.replace("old", "older")));
            RenderedText.ofSize(testRunner, () -> scrolling.render(model.get(), area), 140, 30);
            List.of('j', 'j', 'j').forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
            anew = cursors(dispatched).drop(kept.size());
        }

        // After the bar of the second file, the cursor goes on on the left, where it was.
        Option<Anchor> bar = Option.of(new Anchor.File("b.sql"));
        assertTrue(kept.contains(bar), kept.toString());
        assertEquals(
                Option.of(new Anchor.OnLine("b.sql", ReviewThread.Side.LEFT, 1)),
                kept.get(kept.indexOf(bar) + 1),
                kept.toString());
        // In the other diff, its first line on the new side.
        assertEquals(
                Option.of(Option.of(new Anchor.OnLine("a.sql", ReviewThread.Side.RIGHT, 1))),
                anew.find(cursor -> cursor.exists(Anchor.OnLine.class::isInstance)),
                anew.toString());
    }

    /**
     * Where each move of the cursor took it, as the area told the model, in order.
     */
    private static List<Option<Anchor>> cursors(java.util.List<Msg> dispatched) {
        return List.ofAll(dispatched)
                .filter(ViewMsg.ContentScrolled.class::isInstance)
                .map(msg -> ((ViewMsg.ContentScrolled) msg).to().cursor());
    }

    /**
     * {@code model} with the commit {@code sha} selected, whose diff is {@code diff}.
     */
    private static Model splitCommit(Model model, String sha, String diff) {
        return Update.update(
                        Update.update(model, new SelectionMsg.CommitSelected(new Sha(sha)))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha(sha), model.preferences().diffOptions(), DiffText.of(diff)))
                .model();
    }

    /**
     * The model with a commit selected that changes {@code schema.sql} as {@code lines} say, drawn split (ADR 139).
     */
    private static Model showingSplitCommit(String lines) {
        Model split = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.SPLIT)));
        return Update.update(
                        Update.update(split, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"),
                                split.preferences().diffOptions(),
                                DiffText.of(String.join(
                                        "\n",
                                        "diff --git a/schema.sql b/schema.sql",
                                        "--- a/schema.sql",
                                        "+++ b/schema.sql",
                                        "@@ -1,3 +1,3 @@",
                                        lines))))
                .model();
    }

    @Test
    void aLongRichDiffIsMeasuredWithoutDrawingItsLinesAndOnlyTheLinesThatShowAreDrawn() throws Exception {
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
        AreaId area = counting.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> counting.render(model, area))) {
            SettledPilot.focus(testRunner, area.id());
            String screen = RenderedText.ofLarge(testRunner, () -> counting.render(model, area));
            assertTrue(screen.contains("+ line 0"), screen);
            // Measured without drawing any, as no line of a diff is wider than its file; drawn, only those of an area
            // up to twice as high as the screen: not all of them for the frame the area is first drawn in only to
            // learn its size.
            int shown = gutters.get();
            assertTrue(shown > 0 && shown <= 2 * 30, "drew " + shown);

            // In the size the runner draws in, which can show more lines than the screen above.
            SettledPilot.of(testRunner).pause();
            int settled = gutters.get();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            ViewMsg.ContentScrolled down = (ViewMsg.ContentScrolled) dispatched.getLast();
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
        AreaId area = counting.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> counting.render(model, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> counting.render(model, area));
            int once = styled.get();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();

            // From the header of the commit, its lead, to the bar of its file.
            ViewMsg.ContentScrolled down = (ViewMsg.ContentScrolled) dispatched.getLast();
            assertEquals(Option.of(new Anchor.File("a.txt")), down.to().cursor());
            assertEquals(0, down.to().row());
            Model moved = Update.update(model, down).model();
            RenderedText.ofLarge(testRunner, () -> counting.render(moved, area));
            assertEquals(once, styled.get());
        }
    }

    @Test
    void aClickOnAMarkerOfHunksTheDiffFiltersHideShowsThem() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel clicking = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model model = ContentPanelFixtures.showingCommit("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1,2 +1,2 @@
                 package a;
                -import a.B;
                +import a.C;
                @@ -20,2 +20,2 @@
                 x
                -int a;
                +int b;
                """)
                .mapPreferences(preferences -> preferences.withSettings(
                        preferences.settings().withDiffStyle(DiffStyle.RICH).withDiffFilters(List.of("java-imports"))));
        AreaId area = clicking.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> clicking.render(model, area))) {
            java.util.List<String> screen = RenderedText.ofLarge(testRunner, () -> clicking.render(model, area))
                    .lines()
                    .toList();
            assertTrue(screen.stream().anyMatch(line -> line.contains("filtered changes")), String.join("\n", screen));
            int marker = java.util.stream.IntStream.range(0, screen.size())
                    .filter(row -> screen.get(row).contains("filtered changes"))
                    .findFirst()
                    .getAsInt();
            dev.tamboui.layout.Rect drawn = SettledPilot.of(testRunner).findElement(area.id());

            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, marker);
        }

        assertTrue(
                List.ofAll(dispatched)
                        .exists(net.vanfleteren.jazygit.changes.diff.FilteredMsg.Opened.class::isInstance),
                dispatched.toString());
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
        AreaId area = clicking.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> clicking.render(model, area))) {
            java.util.List<String> screen = RenderedText.ofLarge(testRunner, () -> clicking.render(model, area))
                    .lines()
                    .toList();
            int beta = java.util.stream.IntStream.range(0, screen.size())
                    .filter(row -> screen.get(row).contains("beta"))
                    .findFirst()
                    .getAsInt();
            dev.tamboui.layout.Rect drawn = SettledPilot.of(testRunner).findElement(area.id());

            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, beta);
            ViewMsg.ContentScrolled clicked = (ViewMsg.ContentScrolled) dispatched.getLast();
            assertEquals(
                    Option.of(new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 2)),
                    clicked.to().cursor());
            assertEquals(0, clicked.to().row());

            int sent = dispatched.size();
            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, drawn.x() + 3, drawn.y());
            assertEquals(sent, dispatched.size());
        }
    }
}
