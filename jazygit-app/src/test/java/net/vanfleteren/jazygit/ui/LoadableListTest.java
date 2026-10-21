package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Loadable;
import org.junit.jupiter.api.Test;

/**
 * A click highlights the row clicked on, wherever the list is scrolled to.
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
    void theSelectionGoesToTheOneItemAddedElseItFollowsItsItem() {
        LoadableList.Reselect<List<String>> reselect = LoadableList.Reselect.followingOrAdded(item -> item);

        // b was highlighted; c was added.
        assertEquals(2, reselect.apply(List.of("a", "b"), List.of("a", "b", "c"), 1));
        // b was highlighted; a was removed, so it moved up.
        assertEquals(0, reselect.apply(List.of("a", "b"), List.of("b"), 1));
        // A list of something else altogether, more than one added: none is chosen, b's place stays.
        assertEquals(1, reselect.apply(List.of("a", "b"), List.of("x", "y", "z"), 1));
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
}
