package net.vanfleteren.jazygit.feature.branch;


import net.vanfleteren.jazygit.ui.RenderedText;

import net.vanfleteren.jazygit.state.LoadMsg;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
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
 * The popup asking to stash for a checkout: Enter confirms, Escape cancels.
 */
class StashCheckoutDialogTest {

    private final List<Msg> dispatched = new ArrayList<>();
    private final StashCheckoutDialog dialog = new StashCheckoutDialog(dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        return Update.update(TestModels.loaded(), new CheckoutMsg.NeedsStash("feature")).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(List.of(new CheckoutMsg.StashConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(List.of(new CheckoutMsg.StashCancelled()), dispatched);
    }

    @Test
    void explainsThatTheChangesAreStashedAndPopped() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Checkout feature"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("You must stash and pop your changes"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(enter/esc)"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertEquals(true, open.popup(StashCheckoutPopup.class).isPresent());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(StashCheckoutDialog.ID);
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).orElseThrow());
    }
}
