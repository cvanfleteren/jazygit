package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceDialogTest {

    private record PickPopup(boolean second) implements Popup {
    }

    private record Picked(String what) implements Msg {
        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }

    private final List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<PickPopup> dialog = new ChoiceDialog<>("pick", PickPopup.class,
            popup -> "Pick one",
            List.of(new Choice<>('a', "dialog.discard.all", () -> new Picked("a")),
                    new Choice<>('b', "dialog.discard.unstaged", () -> new Picked("b"),
                            (model, popup) -> popup.second())),
            () -> new Picked("cancel"), dispatched::add);

    private static Model open(boolean second) {
        return TestModels.loaded().openPopup(new PickPopup(second));
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    @Test
    void listsTheOptionsAndAlwaysEndsWithCancel() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            String screen = RenderedText.ofLarge(runner, () -> view(true));
            assertTrue(screen.contains("Pick one"), screen);
            assertTrue(screen.indexOf("(a) Discard all changes") < screen.indexOf("(b) Discard unstaged changes"), screen);
            assertTrue(screen.indexOf("(b) Discard unstaged changes") < screen.indexOf("(c) Cancel"), screen);
            assertTrue(screen.indexOf("(c) Cancel") < screen.indexOf("(enter) select   (esc) cancel"), screen);
        }
    }

    @Test
    void anOptionIsChosenByItsLetterOrByArrowsAndEnter() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            runner.pilot().press('b');
            runner.pilot().pause();
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
            runner.pilot().press(KeyCode.DOWN);
            runner.pilot().press(KeyCode.DOWN);
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(List.of(new Picked("b"), new Picked("a"), new Picked("cancel")), dispatched);
    }

    @Test
    void escapeAndTheCancelLetterCancel() throws Exception {
        try (ToolkitTestRunner runner = start(true)) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
            runner.pilot().press('c');
            runner.pilot().pause();
        }

        assertEquals(List.of(new Picked("cancel"), new Picked("cancel")), dispatched);
    }

    @Test
    void aDisabledOptionCannotBeChosen() throws Exception {
        try (ToolkitTestRunner runner = start(false)) {
            runner.pilot().press('b');
            runner.pilot().pause();
        }

        assertEquals(List.of(), dispatched);
    }

    private ToolkitTestRunner start(boolean second) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(second));
        runner.runner().focusManager().setFocus("pick");
        RenderedText.of(runner, () -> view(second));
        return runner;
    }

    private Element view(boolean second) {
        return stack(text("background"), dialog.render(open(second)).orElseThrow());
    }
}
