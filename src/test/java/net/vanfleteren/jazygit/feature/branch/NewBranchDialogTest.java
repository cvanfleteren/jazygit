package net.vanfleteren.jazygit.feature.branch;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
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

    @Test
    void enterConfirmsTheTypedName() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "topic");
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Confirmed("topic")), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "topic");
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Cancelled()), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded(new SampleData())).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(BranchDialogs.NEW_BRANCH.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            runner.pilot().press(c);
        }
        runner.pilot().pause();
    }
}
