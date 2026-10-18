package net.vanfleteren.jazygit.feature.branch.newbranch;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.branch.BranchDialogs;
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
            new net.vanfleteren.jazygit.ui.widgets.TextInputDialog<>(BranchDialogs.NEW_BRANCH, dispatched::add);
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
        SettledPilot.focus(runner, BranchDialogs.NEW_BRANCH.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
