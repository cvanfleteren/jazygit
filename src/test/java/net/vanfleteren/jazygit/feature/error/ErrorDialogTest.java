package net.vanfleteren.jazygit.feature.error;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The popup telling what went wrong: Enter and Escape both close it.
 */
class ErrorDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<ErrorPopup> dialog = new ConfirmDialog<>(ErrorDialog.SPEC, dispatched::add);
    private final Model open = TestModels.loaded().fail(new AppError.NoUpstream(new Branch.Name("feature")));

    @Test
    void showsTheErrorUnderTheTitleError() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Error"), screen);
            assertTrue(screen.contains("Cannot pull branch feature: it has no upstream"), screen);
            assertTrue(screen.contains("(enter/esc) close"), screen);
        }
    }

    @Test
    void enterCloses() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new ErrorMsg.Dismissed()), dispatched);
    }

    @Test
    void escapeCloses() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new ErrorMsg.Dismissed()), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(ErrorDialog.SPEC.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
