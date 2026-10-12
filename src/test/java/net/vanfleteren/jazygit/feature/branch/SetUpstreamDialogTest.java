package net.vanfleteren.jazygit.feature.branch;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The popup asking where to push a branch without an upstream: it starts out with the branch of the same
 * name on origin, Enter confirms what is in it, Escape cancels.
 */
class SetUpstreamDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.TextInputDialog<SetUpstreamPopup> dialog = new net.vanfleteren.jazygit.ui.widgets.TextInputDialog<>(BranchDialogs.SET_UPSTREAM, dispatched::add);
    // In the test models, feature has no upstream.
    private final Model open = Update.update(TestModels.loaded(), new PushMsg.Requested("feature")).model();

    @Test
    void showsTheTitleAndStartsOutWithTheSameBranchOnOrigin() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Enter upstream as <remote> <branchname>"), screen);
            assertTrue(screen.contains("origin feature"), screen);
        }
    }

    @Test
    void enterConfirmsTheDefault() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new PushMsg.UpstreamConfirmed("origin feature")), dispatched);
    }

    @Test
    void theUpstreamCanBeChanged() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            for (int i = 0; i < "feature".length(); i++) {
                runner.pilot().press(KeyCode.BACKSPACE);
            }
            for (char c : "shared".toCharArray()) {
                runner.pilot().press(c);
            }
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new PushMsg.UpstreamConfirmed("origin shared")), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new PushMsg.UpstreamCancelled()), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertEquals(true, open.popup(SetUpstreamPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(BranchDialogs.SET_UPSTREAM.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
