package net.vanfleteren.jazygit.feature.branch;


import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.git.model.Sha;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;
import static dev.tamboui.toolkit.Toolkit.*;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The popup asking to confirm a force push: Enter confirms, Escape cancels.
 */
class ForcePushDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<ForcePushPopup> dialog = new net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<>(BranchDialogs.FORCE_PUSH, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Model diverged = Update.update(TestModels.loaded(), new LoadMsg.BranchesLoaded(List.of(
                new Branch("main", true, new Sha("aaaa"), Instant.EPOCH, true, false, Option.of(new Upstream(new RemoteBranch("origin", "main"), 1, 2)))))).model();
        return Update.update(diverged, new PushMsg.Requested("main")).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new PushMsg.ForceConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new PushMsg.ForceCancelled()), dispatched);
    }

    @Test
    void warnsThatTheBranchHasDiverged() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Force push main"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Your branch has diverged from the remote branch."), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertEquals(true, open.popup(ForcePushPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(BranchDialogs.FORCE_PUSH.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
