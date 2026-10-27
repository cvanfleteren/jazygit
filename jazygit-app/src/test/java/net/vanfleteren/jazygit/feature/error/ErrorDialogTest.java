package net.vanfleteren.jazygit.feature.error;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup telling what went wrong: Enter and Escape both close it.
 */
class ErrorDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<ErrorPopup> dialog = new ConfirmDialog<>(ErrorDialog.SPEC, dispatched::add);
    private final Model open = TestModels.loaded().fail(new AppError.NoUpstream(new BranchName("feature")));

    @Test
    void showsTheErrorUnderTheTitleError() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Error"), screen);
            assertTrue(screen.contains("Branch feature has no upstream"), screen);
            assertTrue(screen.contains("(enter/esc) close"), screen);
        }
    }

    @Test
    void enterCloses() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Msg.PopupDismissed(ErrorPopup.class)), dispatched);
    }

    @Test
    void escapeCloses() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Msg.PopupDismissed(ErrorPopup.class)), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, ErrorDialog.SPEC.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
