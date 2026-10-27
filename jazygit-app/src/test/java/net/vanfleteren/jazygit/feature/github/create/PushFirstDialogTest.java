package net.vanfleteren.jazygit.feature.github.create;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking to push a branch before creating its pull request: Enter confirms, Escape cancels.
 */
class PushFirstDialogTest {

    private static final BranchName BRANCH = new BranchName("stack-1");
    private static final RemoteBranch TARGET = new RemoteBranch("origin", BRANCH);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<PushFirstPopup> dialog =
            new ConfirmDialog<>(CreatePullRequestDialogs.PUSH_FIRST, dispatched::add);
    private Model open = TestModels.loaded().openPopup(new PushFirstPopup(BRANCH, TARGET, Option.of(1)));

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CreatePullRequestMsg.PushFirstConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Msg.PopupDismissed(PushFirstPopup.class)), dispatched);
    }

    @Test
    void saysHowManyCommitsAreNotPushed() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Push stack-1 first"), screen);
            assertTrue(screen.contains("stack-1 has 1 commit not on origin/stack-1."), screen);
        }
    }

    @Test
    void saysWhereABranchThatTracksNoRemoteBranchGoes() throws Exception {
        open = TestModels.loaded().openPopup(new PushFirstPopup(BRANCH, TARGET, Option.none()));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("stack-1 does not track a remote branch."), screen);
            assertTrue(screen.contains("origin/stack-1 and track it, then create its pull request?"), screen);
        }
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, CreatePullRequestDialogs.PUSH_FIRST.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
