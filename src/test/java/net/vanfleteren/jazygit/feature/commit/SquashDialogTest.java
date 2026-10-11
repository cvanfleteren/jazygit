package net.vanfleteren.jazygit.feature.commit;


import net.vanfleteren.jazygit.ui.RenderedText;

import net.vanfleteren.jazygit.state.LoadMsg;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The popup asking to confirm squashing a commit into the one below it: Enter confirms, Escape cancels.
 */
class SquashDialogTest {

    private final List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<SquashPopup> dialog = SquashDialog.create(dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Commit below = new Commit("c1", "c1", "Ada", "ada@example.com", Instant.EPOCH, "Below", "");
        Commit selected = new Commit("c2", "c2", "Ada", "ada@example.com", Instant.EPOCH, "Selected", "", List.of("c1"));
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(selected, below))).model();
        return Update.update(model, new SquashMsg.Requested(0)).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(List.of(new SquashMsg.Confirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(List.of(new SquashMsg.Cancelled()), dispatched);
    }

    @Test
    void showsTitleAndExplanation() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Squash commit"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Are you sure you want to squash the selected commit"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("the commit below?"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertEquals(true, open.popup(SquashPopup.class).isPresent());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(SquashDialog.ID);
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).orElseThrow());
    }
}
