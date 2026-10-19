package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import org.junit.jupiter.api.Test;

class ChoiceDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final ChoiceDialog<PickPopup> dialog = new ChoiceDialog<>(
            new ChoiceDialog.Spec<>(
                    "pick",
                    PickPopup.class,
                    popup -> "Pick one",
                    List.of(
                            new Choice<>('a', "dialog.discard.all", () -> new Picked("a")),
                            new Choice<>(
                                    'b',
                                    "dialog.discard.unstaged",
                                    () -> new Picked("b"),
                                    (model, popup) -> popup.second())),
                    () -> new Picked("cancel")),
            cursor);

    private static Model open(boolean second) {
        return TestModels.loaded().openPopup(new PickPopup(second));
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(cursor.on(TestModels.loaded())).isEmpty());
    }

    @Test
    void listsTheOptionsAndAlwaysEndsWithCancel() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            String screen = RenderedText.ofLarge(runner, () -> view(true));
            assertTrue(screen.contains("Pick one"), screen);
            assertTrue(
                    screen.indexOf("(a) Discard all changes") < screen.indexOf("(b) Discard unstaged changes"), screen);
            assertTrue(screen.indexOf("(b) Discard unstaged changes") < screen.indexOf("(c) Cancel"), screen);
            assertTrue(screen.indexOf("(c) Cancel") < screen.indexOf("(enter) select   (esc) cancel"), screen);
        }
    }

    @Test
    void anOptionIsChosenByItsLetterOrByArrowsAndEnter() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            SettledPilot.of(runner).press('b');
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("b"), new Picked("a"), new Picked("cancel")), dispatched);
    }

    @Test
    void escapeAndTheCancelLetterCancel() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press('c');
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("cancel"), new Picked("cancel")), dispatched);
    }

    @Test
    void aDisabledOptionCannotBeChosen() throws Exception {
        try (ToolkitTestRunner runner = start(false)) {
            SettledPilot.of(runner).press('b');
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(), dispatched);
    }

    @Test
    void slashFiltersTheOptionsOnTheirTextAndEnterChoosesTheHighlightedOne() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            type(runner, "/uns");
            String screen = RenderedText.ofLarge(runner, () -> view(true));
            assertTrue(screen.contains("/ uns"), screen);
            assertTrue(screen.contains("(b) Discard unstaged changes"), screen);
            assertFalse(screen.contains("(a) Discard all changes"), screen);
            assertFalse(screen.contains("(c) Cancel"), screen);
            assertTrue(screen.contains("(esc) clear filter"), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("b")), dispatched);
    }

    @Test
    void whileFilteringTheLettersGoIntoTheFilter() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            type(runner, "/c");
            String screen = RenderedText.ofLarge(runner, () -> view(true));
            assertTrue(screen.contains("/ c"), screen);
            assertTrue(screen.contains("(a) Discard all changes"), screen);
            assertTrue(screen.contains("(c) Cancel"), screen);
        }

        assertEquals(java.util.List.of(), dispatched);
    }

    @Test
    void escapeClosesTheFilterBeforeItCancels() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            type(runner, "/uns");
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, () -> view(true));
            assertFalse(screen.contains("/ uns"), screen);
            assertTrue(screen.contains("(a) Discard all changes"), screen);
            assertTrue(screen.contains("(enter) select   (esc) cancel   (/) filter"), screen);
            assertEquals(java.util.List.of(), dispatched);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Picked("cancel")), dispatched);
    }

    @Test
    void withoutMatchesItSaysSoAndEnterChoosesNothing() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            type(runner, "/xyz");
            String screen = RenderedText.ofLarge(runner, () -> view(true));
            assertTrue(screen.contains("No matches"), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(), dispatched);
    }

    @Test
    void aHeadingShowsOnlyWhileItsGroupHasAMatchAndTheNewHighlightPreviews() throws Exception {
        ChoiceDialog<PickPopup> grouped = new ChoiceDialog<>(
                new ChoiceDialog.Spec<>(
                        "pick",
                        PickPopup.class,
                        popup -> "Pick one",
                        List.of(
                                new Choice<PickPopup>('a', "dialog.discard.all", () -> new Picked("a"))
                                        .withHeading("dialog.help.global"),
                                new Choice<PickPopup>('b', "dialog.discard.unstaged", () -> new Picked("b"))
                                        .withHeading("dialog.help.legend")
                                        .withPreview(() -> new Picked("preview b"))),
                        () -> new Picked("cancel")),
                cursor);
        Supplier<Element> view = () ->
                stack(text("background"), grouped.render(cursor.on(open(true))).get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            SettledPilot.focus(runner, "pick");
            String before = RenderedText.ofLarge(runner, view);
            assertTrue(before.contains("Global"), before);
            type(runner, "/unst");
            String screen = RenderedText.ofLarge(runner, view);
            assertFalse(screen.contains("Global"), screen);
            assertTrue(screen.contains("Legend"), screen);
            assertTrue(screen.contains("(b) Discard unstaged changes"), screen);
        }

        assertEquals(java.util.List.of(new Picked("preview b")), dispatched);
    }

    private static void type(ToolkitTestRunner runner, String keys) {
        keys.chars().forEach(key -> SettledPilot.of(runner).press((char) key));
        SettledPilot.of(runner).pause();
    }

    private ToolkitTestRunner start(boolean second) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(second));
        SettledPilot.focus(runner, "pick");
        RenderedText.of(runner, () -> view(second));
        return runner;
    }

    private Element view(boolean second) {
        return stack(text("background"), dialog.render(cursor.on(open(second))).get());
    }

    private record PickPopup(boolean second) implements Popup {}

    private record Picked(String what) implements Msg {
        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
