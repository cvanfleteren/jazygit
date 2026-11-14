package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A click highlights the row clicked on, wherever the list is scrolled to. When the value changes, the highlight
 * stays on a row that is there.
 */
class LoadableListTest {

    private static final String ID = "list";
    // Six rows fit inside the border.
    private static final Size SCREEN = new Size(40, 8);

    private final LoadableList<List<String>> list = new LoadableList<>("Items", ID, Theme.Highlights.DEFAULT);

    @Test
    void clickingARowOfAScrolledListHighlightsTheRowShownThere() throws Exception {
        Loadable<List<String>> items = new Loadable.Loaded<>(List.range(0, 20).map(i -> "item " + i));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> list.render(items, all -> all.map(LoadableList.Row::of), true, true), SCREEN)) {
            Pilot pilot = SettledPilot.of(testRunner);
            SettledPilot.focus(testRunner, ID);
            // At the last item, the list shows items 14 to 19.
            pilot.press(KeyCode.END);
            pilot.mousePress(MouseButton.LEFT, 5, 2);

            assertEquals(15, list.selectedIndex());

            pilot.quit();
        }
    }

    @Test
    void clickingEitherLineOfARowWithADetailHighlightsIt() throws Exception {
        Loadable<List<String>> items = new Loadable.Loaded<>(List.range(0, 6).map(i -> "item " + i));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> list.render(
                        items, all -> all.map(item -> LoadableList.Row.of(item).withDetail("detail")), true, true),
                SCREEN)) {
            Pilot pilot = SettledPilot.of(testRunner);
            SettledPilot.focus(testRunner, ID);
            // At the last item, the list shows items 3 to 5, two lines each.
            pilot.press(KeyCode.END);
            pilot.mousePress(MouseButton.LEFT, 5, 4);
            assertEquals(4, list.selectedIndex());

            pilot.mousePress(MouseButton.LEFT, 5, 1);
            assertEquals(3, list.selectedIndex());

            pilot.quit();
        }
    }

    @Test
    void clickingBelowTheLastRowKeepsTheHighlight() throws Exception {
        Loadable<List<String>> items = new Loadable.Loaded<>(List.of("first", "second"));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> list.render(items, all -> all.map(LoadableList.Row::of), true, true), SCREEN)) {
            Pilot pilot = SettledPilot.of(testRunner);
            pilot.mousePress(MouseButton.LEFT, 5, 2);
            pilot.mousePress(MouseButton.LEFT, 5, 5);

            assertEquals(1, list.selectedIndex());

            pilot.quit();
        }
    }

    @Test
    void whenTheHighlightedLastRowIsGoneTheNewLastRowIsHighlighted() {
        show(List.of("a", "b", "c"));
        list.select(2);
        show(List.of("a", "b"));

        assertEquals(1, list.selectedIndex());
        assertEquals(Option.of("b"), list.selected(Function.identity()));
    }

    @Test
    void aHighlightPastTheItemsGivesTheLastItem() {
        show(List.of("a", "b", "c"));
        list.select(5);

        assertEquals(Option.of("c"), list.selected(Function.identity()));
    }

    @Test
    void followingKeepsTheHighlightOnItsItemAndWhereItWasWhenItIsGone() {
        LoadableList<List<String>> following = new LoadableList<>(
                "Items", ID, LoadableList.Reselect.following(Function.identity()), Theme.Highlights.DEFAULT);
        show(following, List.of("a", "b", "c", "d"));
        following.select(1);

        show(following, List.of("x", "a", "b", "c", "d"));
        assertEquals(Option.of("b"), following.selected(Function.identity()));

        // Gone, so the item that came in its place.
        show(following, List.of("x", "a", "c", "d"));
        assertEquals(Option.of("c"), following.selected(Function.identity()));

        show(following, List.of("c", "a", "d"));
        assertEquals(Option.of("c"), following.selected(Function.identity()));
    }

    @Test
    void theScrollbarOfALongerListIsOnTheRightBorderAndMovesWithTheHighlight() {
        Loadable<List<String>> items = new Loadable.Loaded<>(List.range(0, 20).map(i -> "item " + i));

        String top = rightBorder(drawn(items));
        list.select(19);
        String end = rightBorder(drawn(items));

        assertTrue(top.matches("┃+│+"), top);
        assertTrue(end.matches("│+┃+"), end);
    }

    @Test
    void theRightBorderOfAListThatFitsIsLeftAsItIs() {
        String border = rightBorder(drawn(new Loadable.Loaded<>(List.of("first", "second"))));

        assertEquals("│".repeat(SCREEN.height() - 2), border);
    }

    @Test
    void noColumnInsideTheBorderIsKeptForTheScrollbar() {
        String wide = "x".repeat(60);
        for (int count : new int[] {2, 20}) {
            dev.tamboui.buffer.Buffer buffer =
                    drawn(new Loadable.Loaded<>(List.range(0, count).map(i -> wide)));

            // The last column inside the border, on the second row, which is not highlighted.
            assertEquals("x", buffer.get(SCREEN.width() - 2, 2).symbol(), count + " rows");
        }
    }

    @Test
    void theThumbIsInTheTerminalsColourOnATrackInTheColourOfTheBorder() {
        dev.tamboui.buffer.Buffer buffer =
                drawn(new Loadable.Loaded<>(List.range(0, 20).map(i -> "item " + i)));

        int right = SCREEN.width() - 1;
        dev.tamboui.buffer.Cell thumb = buffer.get(right, 1);
        dev.tamboui.buffer.Cell track = buffer.get(right, SCREEN.height() - 2);
        dev.tamboui.buffer.Cell corner = buffer.get(right, 0);
        assertEquals(
                List.of(BorderBar.DOWNWARDS_THUMB, "│"), List.of(thumb, track).map(dev.tamboui.buffer.Cell::symbol));
        // Never in the colour it is found by.
        assertEquals(
                java.util.Optional.of(dev.tamboui.style.Color.RESET),
                thumb.style().fg());
        assertTrue(corner.style().fg().isPresent(), corner.style().toString());
        assertEquals(corner.style().fg(), track.style().fg());
    }

    @Test
    void theLineToFilterOnStaysInsideTheBorderBesideTheScrollbarOfTheList() {
        // As long as the line is wide: the cursor goes to its last column.
        String typed = "a".repeat(SCREEN.width() - 3);
        for (int count : new int[] {2, 20}) {
            dev.tamboui.toolkit.focus.FocusManager focus = new dev.tamboui.toolkit.focus.FocusManager();
            focus.setFocus(ID);
            dev.tamboui.toolkit.element.DefaultRenderContext context =
                    new dev.tamboui.toolkit.element.DefaultRenderContext(
                            focus,
                            new dev.tamboui.toolkit.event.EventRouter(
                                    focus, new dev.tamboui.toolkit.element.ElementRegistry()));
            context.setStyleEngine(Theme.createEngine().get());
            dev.tamboui.layout.Rect area = new dev.tamboui.layout.Rect(0, 0, SCREEN.width(), SCREEN.height());
            dev.tamboui.terminal.Frame frame =
                    dev.tamboui.terminal.Frame.forTesting(dev.tamboui.buffer.Buffer.empty(area));

            list.render(
                            new Loadable.Loaded<>(List.range(0, count).map(i -> "item " + i)),
                            all -> all.map(LoadableList.Row::of),
                            true,
                            true,
                            Pane.title("Items"),
                            Option.of(new FilterLine().field(typed)),
                            event -> {})
                    .render(frame, area, context);

            int border = SCREEN.width() - 1;
            assertTrue(
                    frame.cursorPosition().map(cursor -> cursor.x() < border).orElse(false),
                    count + " rows: " + frame.cursorPosition());
        }
    }

    /**
     * {@code items} drawn in the list, focused, in the dark theme, on the screen of these tests.
     */
    private dev.tamboui.buffer.Buffer drawn(Loadable<List<String>> items) {
        dev.tamboui.toolkit.focus.FocusManager focus = new dev.tamboui.toolkit.focus.FocusManager();
        focus.setFocus(ID);
        dev.tamboui.toolkit.element.DefaultRenderContext context = new dev.tamboui.toolkit.element.DefaultRenderContext(
                focus,
                new dev.tamboui.toolkit.event.EventRouter(focus, new dev.tamboui.toolkit.element.ElementRegistry()));
        context.setStyleEngine(Theme.createEngine().get());
        dev.tamboui.layout.Rect area = new dev.tamboui.layout.Rect(0, 0, SCREEN.width(), SCREEN.height());
        dev.tamboui.buffer.Buffer buffer = dev.tamboui.buffer.Buffer.empty(area);
        list.render(items, all -> all.map(LoadableList.Row::of), true, true)
                .render(dev.tamboui.terminal.Frame.forTesting(buffer), area, context);
        return buffer;
    }

    /**
     * The right border of what {@code buffer} shows, between its corners.
     */
    private static String rightBorder(dev.tamboui.buffer.Buffer buffer) {
        return List.range(1, SCREEN.height() - 1)
                .map(y -> buffer.get(SCREEN.width() - 1, y).symbol())
                .mkString();
    }

    private void show(List<String> items) {
        show(list, items);
    }

    private static void show(LoadableList<List<String>> list, List<String> items) {
        list.render(new Loadable.Loaded<>(items), all -> all.map(LoadableList.Row::of), true, true);
    }
}
