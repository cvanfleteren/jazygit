package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import org.junit.jupiter.api.Test;

/**
 * A {@link ChoiceList} in two columns (ADR 66); in one column, {@link ChoiceDialogTest} tries it.
 */
class ChoiceListTest {

    private static final List<Choice<PickPopup>> FIVE = List.of(
            new Choice<>('a', "dialog.discard.all", () -> new Picked("a")),
            new Choice<>('b', "dialog.discard.unstaged", () -> new Picked("b")),
            new Choice<>('c', "dialog.discard.title", () -> new Picked("c")),
            new Choice<>('d', "dialog.reset.mixed", () -> new Picked("d")),
            new Choice<>('e', "dialog.reset.soft", () -> new Picked("e")));
    // Half of it fits the widest option, "(d) Mixed reset: keep changes, unstaged".
    private static final int WIDE = 90;

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);

    @Test
    void theOptionsFillTheFirstColumnDownThenTheSecond() throws Exception {
        ChoiceList<PickPopup> list = inTwoColumns(FIVE, WIDE);
        try (ToolkitTestRunner runner = start(list)) {
            List<String> lines =
                    List.of(RenderedText.ofLarge(runner, () -> view(list)).split("\n"));
            String first =
                    lines.find(line -> line.contains("(a) Discard all changes")).get();
            assertTrue(first.contains("(d) Mixed reset"), first);
            assertEquals(WIDE / 2, first.indexOf("(d)") - first.indexOf("(a)"), first);
            assertTrue(lines.find(line -> line.contains("(b)")).get().contains("(e)"), lines.mkString("\n"));
            assertFalse(lines.find(line -> line.contains("(c)")).get().contains("(f)"), lines.mkString("\n"));
        }
        assertEquals(3, list.height(open(), new PickPopup()));
    }

    @Test
    void anOptionLeftOutDoesNotCountForTheColumns() throws Exception {
        ChoiceList<PickPopup> list = inTwoColumns(FIVE.update(4, FIVE.get(4).shownWhen((model, popup) -> false)), WIDE);

        assertEquals(2, list.height(open(), new PickPopup()));
    }

    @Test
    void downGoesOnFromTheBottomOfTheFirstColumnToTheTopOfTheSecondAndUpBack() throws Exception {
        assertEquals(new Picked("d"), chosenAfter(KeyCode.DOWN, KeyCode.DOWN, KeyCode.DOWN));
        assertEquals(new Picked("c"), chosenAfter(KeyCode.DOWN, KeyCode.DOWN, KeyCode.DOWN, KeyCode.UP));
    }

    @Test
    void upAndDownStopAtTheFirstAndTheLastOption() throws Exception {
        assertEquals(new Picked("a"), chosenAfter(KeyCode.UP));
        assertEquals(new Picked("e"), chosenAfter(KeyCode.END, KeyCode.DOWN));
    }

    @Test
    void theArrowsToTheSidesGoToTheSameRowOfTheOtherColumn() throws Exception {
        assertEquals(new Picked("e"), chosenAfter(KeyCode.DOWN, KeyCode.RIGHT));
        assertEquals(new Picked("b"), chosenAfter(KeyCode.DOWN, KeyCode.RIGHT, KeyCode.LEFT));
    }

    @Test
    void rightFromARowTheSecondColumnHasNotGoesToItsLastOption() throws Exception {
        assertEquals(new Picked("e"), chosenAfter(KeyCode.DOWN, KeyCode.DOWN, KeyCode.RIGHT));
    }

    @Test
    void leftInTheFirstColumnAndRightInTheSecondDoNothing() throws Exception {
        assertEquals(new Picked("b"), chosenAfter(KeyCode.DOWN, KeyCode.LEFT));
        assertEquals(new Picked("d"), chosenAfter(KeyCode.RIGHT, KeyCode.RIGHT));
    }

    @Test
    void theHighlightIsOnlyInTheColumnOfTheHighlightedOption() throws Exception {
        ChoiceList<PickPopup> list = inTwoColumns(FIVE, WIDE);
        try (ToolkitTestRunner runner = start(list)) {
            assertEquals(List.of(true, false), reversed(runner, list));
            press(runner, KeyCode.RIGHT);
            assertEquals(List.of(false, true), reversed(runner, list));
        }
    }

    /**
     * Whether the first line of the left column, and of the right one, is drawn reversed, as a highlight is without
     * a theme.
     */
    private List<Boolean> reversed(ToolkitTestRunner runner, ChoiceList<PickPopup> list) throws Exception {
        CompletableFuture<List<Boolean>> reversed = new CompletableFuture<>();
        runner.runner().runOnRenderThread(() -> {
            Rect area = new Rect(0, 0, WIDE, 3);
            Buffer buffer = Buffer.empty(area);
            list.render(cursor.on(open()), new PickPopup())
                    .render(Frame.forTesting(buffer), area, RenderContext.empty());
            reversed.complete(List.of(0, WIDE / 2)
                    .map(x -> buffer.get(x, 0).style().addModifiers().contains(Modifier.REVERSED)));
        });
        return reversed.get(5, TimeUnit.SECONDS);
    }

    @Test
    void anOptionTooWideForHalfTheWidthPutsTheListInOneColumn() throws Exception {
        ChoiceList<PickPopup> list = inTwoColumns(FIVE, 50);
        try (ToolkitTestRunner runner = start(list)) {
            List<String> lines =
                    List.of(RenderedText.ofLarge(runner, () -> view(list)).split("\n"));
            assertFalse(lines.find(line -> line.contains("(a)")).get().contains("(d)"), lines.mkString("\n"));
            press(runner, KeyCode.RIGHT);
            press(runner, KeyCode.ENTER);
        }
        assertEquals(5, inTwoColumns(FIVE, 50).height(open(), new PickPopup()));
        assertEquals(java.util.List.of(new Picked("a")), dispatched);
    }

    @Test
    void headingsAreRefusedInTwoColumns() {
        List<Choice<PickPopup>> grouped = FIVE.update(3, FIVE.get(3).withHeading("dialog.help.global"));

        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> inTwoColumns(grouped, WIDE));
        assertTrue(refused.getMessage().contains("two columns"), refused.getMessage());
    }

    private Msg chosenAfter(KeyCode... keys) throws Exception {
        dispatched.clear();
        cursor.reset();
        ChoiceList<PickPopup> list = inTwoColumns(FIVE, WIDE);
        try (ToolkitTestRunner runner = start(list)) {
            for (KeyCode key : keys) {
                press(runner, key);
            }
            press(runner, KeyCode.ENTER);
        }
        assertEquals(1, dispatched.size(), dispatched.toString());
        return dispatched.getFirst();
    }

    private static void press(ToolkitTestRunner runner, KeyCode key) {
        SettledPilot.of(runner).press(key);
        SettledPilot.of(runner).pause();
    }

    private ChoiceList<PickPopup> inTwoColumns(List<Choice<PickPopup>> choices, int width) {
        return ChoiceList.inTwoColumns("pick", choices, () -> new Picked("cancel"), cursor, width);
    }

    private ToolkitTestRunner start(ChoiceList<PickPopup> list) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(list));
        SettledPilot.focus(runner, "pick");
        RenderedText.of(runner, () -> view(list));
        return runner;
    }

    private Element view(ChoiceList<PickPopup> list) {
        return stack(text("background"), list.render(cursor.on(open()), new PickPopup()));
    }

    private static Model open() {
        return TestModels.loaded().openPopup(new PickPopup());
    }

    private record PickPopup() implements Popup {}

    private record Picked(String what) implements Msg {
        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
