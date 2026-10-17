package net.vanfleteren.jazygit.feature.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking what a new pull request says: the title starts out as given, the body empty and the draft box
 * unticked; Enter in the title or Ctrl+S anywhere creates it, Escape cancels.
 */
class NewPullRequestDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final NewPullRequestDialog dialog = new NewPullRequestDialog(dispatched::add);
    private final Model open = TestModels.loaded()
            .openPopup(new NewPullRequestPopup(
                    new Branch.Name("feature"), new Branch.Name("feature"), new Branch.Name("main"), "Add login"));

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            SettledPilot.of(runner).press(c);
        }
        SettledPilot.of(runner).pause();
    }

    @Test
    void showsTheDraftBoxTheTitleAndTheBody() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Create pull request: 'feature' into 'main'"), screen);
            assertTrue(screen.contains("[ ] Draft?"), screen);
            assertTrue(screen.contains("Title"), screen);
            assertTrue(screen.contains("Add login"), screen);
            assertTrue(screen.contains("Body"), screen);
        }
    }

    @Test
    void enterInTheTitleCreatesItAsIs() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new PullRequestMsg.CreateConfirmed(new NewPullRequest("Add login", "", false))),
                dispatched);
    }

    @Test
    void theBodyAndTheDraftBoxAreTakenAlong() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+S opens the settings, after the fields had their chance.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.hasCtrl() && key.isChar('s')
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            type(runner, "!");
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "Why");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "it matters");
            SettledPilot.focus(runner, NewPullRequestDialog.DRAFT_ID);
            SettledPilot.of(runner).press(' ');
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("[x] Draft?"), screen);
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(
                        new PullRequestMsg.CreateConfirmed(new NewPullRequest("Add login!", "Why\nit matters", true))),
                dispatched);
    }

    @Test
    void escapeCancelsFromAnyField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app has a global Escape, which gets the key before the dialog around the fields does.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.code() == KeyCode.ESCAPE
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.focus(runner, NewPullRequestDialog.BODY_ID);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.focus(runner, NewPullRequestDialog.DRAFT_ID);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(
                        new PullRequestMsg.CreateCancelled(),
                        new PullRequestMsg.CreateCancelled(),
                        new PullRequestMsg.CreateCancelled()),
                dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, NewPullRequestDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(open).get();
    }
}
