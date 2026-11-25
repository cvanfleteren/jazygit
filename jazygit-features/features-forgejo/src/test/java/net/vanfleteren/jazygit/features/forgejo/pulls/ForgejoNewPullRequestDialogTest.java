package net.vanfleteren.jazygit.features.forgejo.pulls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking what a new pull request on Forgejo merges into and what it says, laid out as GitHub's (ADR 175,
 * 178): the bases, the title, the body and the draft box, without a box to stack it.
 */
class ForgejoNewPullRequestDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded()
            .openPopup(NewPullRequestPopup.opening(
                    new BranchName("feature"),
                    List.of(new BranchName("main"), new BranchName("release")),
                    "Add login"));
    // As in the app, an edit or a move changes the model the popup shows; the other messages are only kept.
    private final ForgejoNewPullRequestDialog dialog = new ForgejoNewPullRequestDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof CreatePullRequestMsg.Edited || msg instanceof NewPullRequestMsg.BaseMoved) {
            model = Update.update(model, msg).model();
        }
    });

    @Test
    void showsTheBasesThenTheTitleTheBodyAndTheDraftBox() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Create pull request: 'feature'"), screen);
            assertTrue(screen.contains("Into: main"), screen);
            assertTrue(screen.indexOf("main") < screen.indexOf("release"), screen);
            assertTrue(screen.indexOf("release") < screen.indexOf("Title"), screen);
            assertTrue(screen.contains("Add login"), screen);
            assertTrue(screen.indexOf("Title") < screen.indexOf("Body"), screen);
            assertTrue(screen.indexOf("Body") < screen.indexOf("[ ] Draft?"), screen);
            assertTrue(screen.contains("tab: next field  ↑↓: base  type: filter  space: tick draft"), screen);
            assertFalse(screen.contains("Stack on"), screen);
        }
    }

    @Test
    void enterInTheTitleCreatesIt() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CreatePullRequestMsg.Confirmed()), dispatched);
    }

    @Test
    void spaceOnTheDraftBoxTicksIt() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, ForgejoNewPullRequestDialog.DRAFT_ID);
            SettledPilot.of(runner).press(' ');
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("[x] Draft?"), screen);
        }

        assertEquals(java.util.List.of(new CreatePullRequestMsg.Edited("Add login", "", true)), dispatched);
    }

    @Test
    void escapeCancelsIt() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PopupMsg.Dismissed(NewPullRequestPopup.class)), dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, ForgejoNewPullRequestDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
