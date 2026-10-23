package net.vanfleteren.jazygit.feature.branch.push;

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
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking where to push a branch without an upstream: it starts out with the branch of the same
 * name on origin, Enter confirms what is in it, Escape cancels.
 */
class SetUpstreamDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    // In the test models, feature has no upstream.
    private Model open = Update.update(TestModels.loaded(), new PushMsg.Requested(new BranchName("feature")))
            .model();
    // As in the app, an edit changes the model the popup shows; the other messages are only kept.
    private final net.vanfleteren.jazygit.ui.widgets.TextInputDialog<SetUpstreamPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.TextInputDialog<>(PushDialogs.SET_UPSTREAM, msg -> {
                dispatched.add(msg);
                if (msg instanceof Msg.TextEdited) {
                    open = Update.update(open, msg).model();
                }
            });

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
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PushMsg.UpstreamConfirmed()), dispatched);
        assertEquals(
                Option.of("origin feature"), open.popup(SetUpstreamPopup.class).map(SetUpstreamPopup::upstream));
    }

    @Test
    void theUpstreamCanBeChanged() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            for (int i = 0; i < "feature".length(); i++) {
                SettledPilot.of(runner).press(KeyCode.BACKSPACE);
            }
            for (char c : "shared".toCharArray()) {
                SettledPilot.of(runner).press(c);
            }
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(new PushMsg.UpstreamConfirmed(), dispatched.getLast());
        assertEquals(
                Option.of("origin shared"), open.popup(SetUpstreamPopup.class).map(SetUpstreamPopup::upstream));
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PushMsg.UpstreamCancelled()), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(SetUpstreamPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, PushDialogs.SET_UPSTREAM.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
