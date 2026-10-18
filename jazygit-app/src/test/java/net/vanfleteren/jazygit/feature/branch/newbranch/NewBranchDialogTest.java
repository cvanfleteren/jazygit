package net.vanfleteren.jazygit.feature.branch.newbranch;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking for the name of a new branch: Enter confirms what was typed, Escape cancels.
 */
class NewBranchDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.TextInputDialog<NewBranchPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.TextInputDialog<>(NewBranchDialogs.NEW_BRANCH, dispatched::add);
    private final Model open = Update.update(
                    TestModels.loaded(new SampleData()), new NewBranchMsg.Requested(new Branch.Name("main")))
            .model();

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            SettledPilot.of(runner).press(c);
        }
        SettledPilot.of(runner).pause();
    }

    @Test
    void enterConfirmsTheTypedName() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "topic");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Confirmed("topic")), dispatched);
    }

    @Test
    void theReadlineKeysEditTheName() throws Exception {
        java.util.List<KeyEvent> global = new ArrayList<>();
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+W shows whitespace in the diffs, after the field had its chance.
            runner.runner().eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent key) {
                    global.add(key);
                }
                return EventResult.UNHANDLED;
            });
            type(runner, "feature/foo-bar");
            SettledPilot.of(runner).press(KeyCode.LEFT, KeyModifiers.CTRL);
            SettledPilot.of(runner).press('w', KeyModifiers.CTRL);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Confirmed("bar")), dispatched);
        assertEquals(java.util.List.of(), global);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "topic");
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Cancelled()), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded(new SampleData())).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, NewBranchDialogs.NEW_BRANCH.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
