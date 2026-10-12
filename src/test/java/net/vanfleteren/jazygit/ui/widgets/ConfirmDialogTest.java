package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmDialogTest {

    private record AskPopup(String thing) implements Popup {
    }

    private record Yes() implements Msg {
        @Override
        public net.vanfleteren.jazygit.state.Update.Next apply(Model model) {
            return net.vanfleteren.jazygit.state.Update.Next.of(model);
        }
    }

    private record No() implements Msg {
        @Override
        public net.vanfleteren.jazygit.state.Update.Next apply(Model model) {
            return net.vanfleteren.jazygit.state.Update.Next.of(model);
        }
    }

    private static final String LONG = "This is a long question that needs several lines at the width of the dialog, "
            + "so that the hint below it must still stay visible after the message has been wrapped. Really?";

    private final java.util.List<Msg> dispatched = new ArrayList<>();

    private ConfirmDialog<AskPopup> dialog(String message) {
        return new ConfirmDialog<>("ask", AskPopup.class, popup -> "Delete " + popup.thing(), popup -> message,
                Yes::new, No::new, dispatched::add);
    }

    private static Model open() {
        return TestModels.loaded().openPopup(new AskPopup("foo"));
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog("Sure?").render(TestModels.loaded()).isEmpty());
    }

    @Test
    void showsTitleMessageAndTheStandardHint() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog("Are you sure?");
        try (ToolkitTestRunner runner = start(dialog)) {
            String screen = RenderedText.ofLarge(runner, () -> view(dialog));
            assertTrue(screen.contains("Delete foo"), screen);
            assertTrue(screen.contains("Are you sure?"), screen);
            assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void aLongMessageWrapsAndKeepsTheHintVisible() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog(LONG);
        try (ToolkitTestRunner runner = start(dialog)) {
            String screen = RenderedText.ofLarge(runner, () -> view(dialog));
            assertTrue(screen.contains("Really?"), screen);
            assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void enterConfirmsAndEscapeCancels() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog("Sure?");
        try (ToolkitTestRunner runner = start(dialog)) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new Yes(), new No()), dispatched);
    }

    @Test
    void countsTheWrappedLines() {
        assertEquals(1, ConfirmDialog.lines("short", 56));
        assertEquals(2, ConfirmDialog.lines("a".repeat(40) + " " + "b".repeat(40), 56));
    }

    private ToolkitTestRunner start(ConfirmDialog<AskPopup> dialog) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(dialog));
        runner.runner().focusManager().setFocus("ask");
        RenderedText.of(runner, () -> view(dialog));
        return runner;
    }

    private Element view(ConfirmDialog<AskPopup> dialog) {
        return stack(text("background"), dialog.render(open()).get());
    }
}
