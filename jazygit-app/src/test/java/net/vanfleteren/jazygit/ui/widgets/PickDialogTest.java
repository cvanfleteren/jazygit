package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

class PickDialogTest {

    private static final List<String> BRANCHES = List.of("main", "develop", "release");
    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final PickDialog<BranchPopup, String> dialog = new PickDialog<>(
            new PickDialog.Spec<>(
                    "pick",
                    BranchPopup.class,
                    popup -> "Merge into",
                    popup -> popup.branches().map(PickDialog.Row::of),
                    Picked::new),
            cursor);

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(cursor.on(TestModels.loaded())).isEmpty());
    }

    @Test
    void listsTheNamesOfThePopup() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Merge into"), screen);
            assertTrue(screen.indexOf("main") < screen.indexOf("develop"), screen);
            assertTrue(screen.indexOf("develop") < screen.indexOf("release"), screen);
            assertTrue(screen.contains("(enter) pick"), screen);
        }
    }

    @Test
    void aNameIsPickedWithTheArrowsAndEnterAndEscapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new Picked("main"), new Picked("release"), new Msg.PopupDismissed(BranchPopup.class)),
                dispatched);
    }

    @Test
    void withoutNamesItSaysSoAndOnlyCancels() throws Exception {
        PickDialog<BranchPopup, String> empty = new PickDialog<>(
                new PickDialog.Spec<>(
                        "pick",
                        BranchPopup.class,
                        popup -> "Merge into",
                        popup -> popup.branches().map(PickDialog.Row::of),
                        Picked::new,
                        Option.of("dialog.recentRepos.none")),
                cursor);
        Supplier<Element> view = () -> stack(
                text("background"),
                empty.render(cursor.on(TestModels.loaded().openPopup(new BranchPopup(List.empty()))))
                        .get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            SettledPilot.focus(runner, "pick");
            String screen = RenderedText.ofLarge(runner, view);
            assertTrue(screen.contains("No other recent repositories"), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Msg.PopupDismissed(BranchPopup.class)), dispatched);
    }

    @Test
    void cellsAreCutPastTheirCapAndColumnsAreAsWideAsTheirWidestCell() {
        List<PickDialog.Row<String>> rows = List.of(
                new PickDialog.Row<>("a", List.of(PickDialog.Cell.of("abcdefgh").capped(5), PickDialog.Cell.of("x"))),
                new PickDialog.Row<>("b", List.of(PickDialog.Cell.of("ab").capped(5), PickDialog.Cell.of("xyz"))));

        assertEquals("abcd…", rows.head().cells().head().shown());
        assertEquals("ab", rows.last().cells().head().shown());
        assertEquals(List.of(5, 3), PickDialog.widths(rows));
        assertEquals(Option.of("abcdefgh\nx"), PickDialog.details(rows.head()));
        assertEquals(Option.none(), PickDialog.details(rows.last()));
    }

    @Test
    void cellsOfWideCharactersAreCutAndSizedInColumns() {
        List<PickDialog.Row<String>> rows = List.of(
                new PickDialog.Row<>("a", List.of(PickDialog.Cell.of("ab👍👍👍").capped(5))),
                new PickDialog.Row<>("b", List.of(PickDialog.Cell.of("日本").capped(5))));

        // Never half an emoji; the ellipsis within the five columns.
        assertEquals("ab👍…", rows.head().cells().head().shown());
        assertEquals("日本", rows.last().cells().head().shown());
        assertEquals(List.of(5), PickDialog.widths(rows));
    }

    @Test
    void theHighlightedRowShowsInFullUnderThePopupWhenItIsCut() throws Exception {
        PickDialog<BranchPopup, String> columns = new PickDialog<>(
                new PickDialog.Spec<>(
                        "pick",
                        BranchPopup.class,
                        popup -> "Repositories",
                        popup -> popup.branches()
                                .map(name -> new PickDialog.Row<>(
                                        name,
                                        List.of(PickDialog.Cell.of(name).capped(8), PickDialog.Cell.of("~/repos")))),
                        Picked::new),
                cursor);
        Supplier<Element> view = () -> stack(
                text("background"),
                columns.render(cursor.on(
                                TestModels.loaded().openPopup(new BranchPopup(List.of("short", "a-very-long-name")))))
                        .get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            SettledPilot.focus(runner, "pick");
            String first = RenderedText.ofLarge(runner, view);
            assertTrue(first.contains("short     ~/repos"), first);
            assertTrue(first.contains("a-very-…  ~/repos"), first);
            assertFalse(first.contains("a-very-long-name"), first);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).pause();
            String second = RenderedText.ofLarge(runner, view);
            assertTrue(second.contains("a-very-long-name"), second);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("a-very-long-name")), dispatched);
    }

    @Test
    void slashFiltersTheRowsOnAnyCellKeepingTheHighlightedRowWhileItMatches() throws Exception {
        PickDialog<BranchPopup, String> columns = new PickDialog<>(
                new PickDialog.Spec<>(
                        "pick",
                        BranchPopup.class,
                        popup -> "Repositories",
                        popup -> popup.branches()
                                .map(name -> new PickDialog.Row<>(
                                        name,
                                        List.of(PickDialog.Cell.of(name), PickDialog.Cell.of("~/" + name + "-dir")))),
                        Picked::new),
                cursor);
        Supplier<Element> view = () -> stack(
                text("background"),
                columns.render(cursor.on(
                                TestModels.loaded().openPopup(new BranchPopup(List.of("main", "develop", "release")))))
                        .get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            SettledPilot.focus(runner, "pick");
            RenderedText.of(runner, view);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            type(runner, "/e");
            String screen = RenderedText.ofLarge(runner, view);
            assertTrue(screen.contains("/ e"), screen);
            assertFalse(screen.contains("main"), screen);
            assertTrue(screen.contains("develop"), screen);
            assertTrue(screen.contains("(esc) clear filter"), screen);
            type(runner, "-dir");
            String cell = RenderedText.ofLarge(runner, view);
            assertTrue(cell.contains("release"), cell);
            assertFalse(cell.contains("develop"), cell);
            type(runner, "x");
            String none = RenderedText.ofLarge(runner, view);
            assertTrue(none.contains("No matches"), none);
            SettledPilot.of(runner).press(KeyCode.BACKSPACE);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("release")), dispatched);
    }

    @Test
    void filteringKeepsTheHighlightedRowWhenOthersBeforeItAreGone() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            type(runner, "/e");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("release")), dispatched);
    }

    @Test
    void theReadlineKeysEditTheFilter() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "/rel");
            SettledPilot.of(runner).press('w', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("main"), screen);
            assertTrue(screen.contains("develop"), screen);
        }
    }

    @Test
    void escapeClosesTheFilterBeforeItCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "/rel");
            String filtered = RenderedText.ofLarge(runner, this::view);
            assertFalse(filtered.contains("develop"), filtered);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("develop"), screen);
            assertTrue(screen.contains("(/) filter"), screen);
            assertEquals(java.util.List.of(), dispatched);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Msg.PopupDismissed(BranchPopup.class)), dispatched);
    }

    private static void type(ToolkitTestRunner runner, String keys) {
        keys.chars().forEach(key -> SettledPilot.of(runner).press((char) key));
        SettledPilot.of(runner).pause();
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, "pick");
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(
                text("background"),
                dialog.render(cursor.on(TestModels.loaded().openPopup(new BranchPopup(BRANCHES))))
                        .get());
    }

    private record BranchPopup(List<String> branches) implements Popup {}

    private record Picked(String what) implements Msg {
        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
