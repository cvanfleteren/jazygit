package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * How an area of the content panel draws its lines: the bars on its borders that say how far it is scrolled, the
 * lines it draws of all it is given, and the backgrounds that go on to its edge.
 */
class AreaDrawingTest {

    @Test
    void theBottomBorderOfAnAreaWithLinesWiderThanItShowsHowFarItIsScrolledSideways() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                ContentPanelFixtures.showingCommit("+" + "x".repeat(150) + "END"));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String left;
        String right;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area.id());
            left = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.range(0, 80).forEach(i -> SettledPilot.of(testRunner).press(KeyCode.RIGHT));
            SettledPilot.of(testRunner).pause();
            right = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
        }

        assertTrue(java.util.regex.Pattern.compile("[╰└]━+─+[╯┘]").matcher(left).find(), left);
        assertTrue(
                java.util.regex.Pattern.compile("[╰└]─+━+[╯┘]").matcher(right).find(), right);
    }

    @Test
    void theRightBorderOfAnAreaWithMoreLinesThanShowShowsHowFarItIsScrolledDown() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(ContentPanelFixtures.showingCommit(
                        List.range(0, 200).map(i -> "+line " + i).mkString("\n")));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        List<String> top;
        List<String> end;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area.id());
            top = List.of(RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area))
                    .split("\n"));
            SettledPilot.of(testRunner).press(KeyCode.END);
            SettledPilot.of(testRunner).pause();
            end = List.of(RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area))
                    .split("\n"));
        }

        // The last column of the lines between the corners of the area: its right border.
        java.util.function.Function<List<String>, String> rightBorder =
                screen -> screen.dropWhile(row -> !row.contains("╭") && !row.contains("┌"))
                        .tail()
                        .takeWhile(row -> !row.contains("╰") && !row.contains("└"))
                        .map(row -> row.substring(row.length() - 1))
                        .mkString();
        assertTrue(rightBorder.apply(top).matches("┃+│+"), String.join("\n", top));
        assertTrue(rightBorder.apply(end).matches("│+┃+"), String.join("\n", end));
    }

    @Test
    void theTrackOnTheBottomBorderIsTheLineOfTheBorderAsDoubleAsTheThemeDrawsIt() {
        Model model = ContentPanelFixtures.showingCommit("+" + "x".repeat(150) + "END");
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {});
        AreaId area = panel.areaOfPane(model, CommitsPanel.PANE).get();

        // The none theme borders the area that has the focus with a double line.
        dev.tamboui.buffer.Buffer buffer =
                themed(panel.render(model, area), area.id(), net.vanfleteren.jazygit.settings.ThemeName.NONE);

        String screen = List.range(0, buffer.area().height())
                .map(y -> List.range(0, buffer.area().width())
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString())
                .mkString("\n");
        assertTrue(java.util.regex.Pattern.compile("╚━+═+╝").matcher(screen).find(), screen);
    }

    @Test
    void theBarsOnTheRightAndBottomBordersHaveThumbsInTheTerminalsColourOnTracksInThatOfTheBorder() {
        // Taller and wider than the area: both bars show, their thumbs at the start.
        Model model = ContentPanelFixtures.showingCommit(
                List.range(0, 100).map(i -> "+" + "x".repeat(150)).mkString("\n"));
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {});
        AreaId area = panel.areaOfPane(model, CommitsPanel.PANE).get();

        dev.tamboui.buffer.Buffer buffer =
                themed(panel.render(model, area), area.id(), net.vanfleteren.jazygit.settings.ThemeName.DARK);

        int bottom = buffer.area().height() - 1;
        int right = buffer.area().width() - 1;
        dev.tamboui.buffer.Cell corner = buffer.get(right, bottom);
        List<dev.tamboui.buffer.Cell> thumbs = List.of(buffer.get(1, bottom), buffer.get(right, 1));
        List<dev.tamboui.buffer.Cell> tracks = List.of(buffer.get(right - 1, bottom), buffer.get(right, bottom - 1));
        assertEquals(
                List.of(
                        net.vanfleteren.jazygit.ui.widgets.BorderBar.SIDEWAYS_THUMB,
                        net.vanfleteren.jazygit.ui.widgets.BorderBar.DOWNWARDS_THUMB,
                        "─",
                        "│"),
                thumbs.appendAll(tracks).map(dev.tamboui.buffer.Cell::symbol));
        assertTrue(
                thumbs.forAll(
                        thumb -> thumb.style().fg().map(Color.RESET::equals).orElse(true)),
                thumbs.map(dev.tamboui.buffer.Cell::style).toString());
        assertTrue(corner.style().fg().isPresent(), corner.style().toString());
        assertEquals(
                List.fill(2, corner.style().fg()),
                tracks.map(track -> track.style().fg()));
        // No column inside the border is kept for a bar: a long line goes on to it.
        int line = List.range(0, bottom)
                .find(y -> buffer.get(1, y).symbol().equals("+"))
                .get();
        assertEquals("x", buffer.get(right - 1, line).symbol());
    }

    /**
     * What {@code view} draws on a screen 100 by 30 in {@code theme}, while {@code focused} has the focus.
     */
    private static dev.tamboui.buffer.Buffer themed(
            dev.tamboui.toolkit.element.Element view,
            String focused,
            net.vanfleteren.jazygit.settings.ThemeName theme) {
        dev.tamboui.css.engine.StyleEngine engine = Theme.createEngine().get();
        Theme.activate(engine, theme);
        dev.tamboui.toolkit.focus.FocusManager focus = new dev.tamboui.toolkit.focus.FocusManager();
        focus.setFocus(focused);
        dev.tamboui.toolkit.element.DefaultRenderContext context = new dev.tamboui.toolkit.element.DefaultRenderContext(
                focus,
                new dev.tamboui.toolkit.event.EventRouter(focus, new dev.tamboui.toolkit.element.ElementRegistry()));
        context.setStyleEngine(engine);
        dev.tamboui.layout.Rect size = new dev.tamboui.layout.Rect(0, 0, 100, 30);
        dev.tamboui.buffer.Buffer buffer = dev.tamboui.buffer.Buffer.empty(size);
        view.render(dev.tamboui.terminal.Frame.forTesting(buffer), size, context);
        return buffer;
    }

    @Test
    void theBottomBorderOfAnAreaWithLinesNoWiderThanItIsLeftAsItIs() throws Exception {
        Model model = ContentPanelFixtures.showingCommit("+short");
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {});
        AreaId area = panel.areaOfPane(model, CommitsPanel.PANE).get();

        String screen;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, area));
        }

        assertTrue(java.util.regex.Pattern.compile("[╰└]─+[╯┘]").matcher(screen).find(), screen);
        // Nor are there more lines than show: the right border is left as it is too.
        assertFalse(screen.contains(net.vanfleteren.jazygit.ui.widgets.BorderBar.DOWNWARDS_THUMB), screen);
        assertFalse(
                java.util.regex.Pattern.compile("[╰└][─━]*━[─━]*[╯┘]")
                        .matcher(screen)
                        .find(),
                screen);
    }

    @Test
    void allTheLinesThatShowAreScrolledSidewaysAlsoWhenTheAreaGrowsAtTheBottom() throws Exception {
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(ContentPanelFixtures.showingCommit(List.range(0, 200)
                        .map(i -> "+%03d".formatted(i) + "x".repeat(150))
                        .mkString("\n")));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area.id());
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
                new java.util.concurrent.atomic.AtomicReference<>(ContentPanelFixtures.showingCommit(
                        List.range(0, 2000).map(i -> "+line %04d".formatted(i)).mkString("\n")));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> false,
                id -> {},
                msg -> model.set(Update.update(model.get(), msg).model()));
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area.id());
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
                TextWindow.windowed(lines, 1, 40, 10, 0, row -> false).lines();

        assertEquals(100, windowed.size());
        assertEquals(20, drawn.get());
        // From where it is scrolled to, as many lines as an area twice as high shows, cut sideways.
        assertEquals("40", windowed.get(40).rawContent());
        assertEquals("59", windowed.get(59).rawContent());
        assertEquals("", windowed.get(39).rawContent());
        assertEquals("", windowed.get(60).rawContent());
        // At the end, the lines an area twice as high would show from its last line at its bottom.
        java.util.List<Line> atEnd =
                TextWindow.windowed(lines, 0, 95, 10, 0, row -> false).lines();
        assertEquals("080", atEnd.get(80).rawContent());
        assertEquals("", atEnd.get(79).rawContent());
        // Before the area was ever drawn, none of them, though it still gets as many.
        int before = drawn.get();
        java.util.List<Line> undrawn =
                TextWindow.windowed(lines, 0, 50, 0, 0, row -> false).lines();
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
                TextWindow.filled(changed, 14));
        // A line as wide already, and one that does not end in padding, are left as they are.
        assertEquals(changed, TextWindow.filled(changed, 9));
        Line context = Line.from(Span.raw(" 1 │ "), Span.raw(" ab"));
        assertEquals(context, TextWindow.filled(context, 14));
    }

    @Test
    void onlyTheLinesThatFillAreDrawnToTheEdgeAndThenScrolledSideways() {
        Style green = Style.EMPTY.bg(Color.GREEN);
        Rows lines = Rows.of(io.vavr.collection.Vector.of(
                Line.from(Span.raw("ab"), Span.styled(" ", green)),
                Line.from(Span.raw("ab"), Span.styled(" ", green))));

        java.util.List<Line> windowed =
                TextWindow.windowed(lines, 1, 0, 10, 6, row -> row == 0).lines();

        // Filled to the edge of the area where it is scrolled to, then cut sideways.
        assertEquals(Line.from(Span.raw("b"), Span.styled("     ", green)), windowed.get(0));
        assertEquals(Line.from(Span.raw("b"), Span.styled(" ", green)), windowed.get(1));
    }

    @Test
    void theBackgroundOfAChangedLineGoesOnToTheEdgeOfTheArea() throws Exception {
        ContentPanel filling = new ContentPanel(ContentPanelFixtures.GREEN_ADDED, () -> false, id -> {}, msg -> {});
        Model model = ContentPanelFixtures.showingRichCommit("+short\n+a bit longer");
        AreaId area = filling.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> filling.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            dev.tamboui.buffer.Buffer buffer =
                    ContentPanelFixtures.drawn(testRunner, () -> filling.render(model, area), 100);
            // From its marker to the border, past the widest line of the file, not just as wide as that: from the
            // first frame, though the area only learns how wide it is while it draws.
            ContentPanelFixtures.assertBackgroundTo(Color.GREEN, "+ short", 98, buffer);
            // The bar of the file too.
            ContentPanelFixtures.assertBackgroundTo(Color.BLUE, "added  schema.sql", 98, buffer);
        }
    }

    @Test
    void aBarWiderThanTheLinesOfItsFileGoesOnToTheEdgeOfTheArea() throws Exception {
        ContentPanel filling = new ContentPanel(ContentPanelFixtures.GREEN_ADDED, () -> false, id -> {}, msg -> {});
        // The bar is the widest line of its file: it is not padded to the width of the file.
        Model model = ContentPanelFixtures.showingRichCommit("+x");
        AreaId area = filling.areaOfPane(model, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> filling.render(model, area), new dev.tamboui.layout.Size(100, 30))) {
            ContentPanelFixtures.assertBackgroundTo(
                    Color.BLUE,
                    "added  schema.sql",
                    98,
                    ContentPanelFixtures.drawn(testRunner, () -> filling.render(model, area), 100));
        }
    }
}
