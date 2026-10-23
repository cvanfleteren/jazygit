package net.vanfleteren.jazygit.feature.branch.push;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking to confirm a force push: Enter confirms, Escape cancels.
 */
class ForcePushDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<ForcePushPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<>(PushDialogs.FORCE_PUSH, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Model diverged = TestModels.localBranchesRead(
                        TestModels.loaded(),
                        List.of(Fixtures.branch(
                                new BranchName("main"),
                                true,
                                new Sha("aaaa"),
                                Instant.EPOCH,
                                true,
                                false,
                                Option.of(new Upstream(
                                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))),
                                        1,
                                        2)))))
                .model();
        return Update.update(diverged, new PushMsg.Requested(new BranchName("main")))
                .model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PushMsg.ForceConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PushMsg.ForceCancelled()), dispatched);
    }

    @Test
    void warnsThatTheBranchHasDiverged() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Force push main"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(
                    screen.contains("Your branch has diverged from the remote branch."), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(ForcePushPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, PushDialogs.FORCE_PUSH.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
