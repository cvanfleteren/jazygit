package net.vanfleteren.jazygit.feature.github.create;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.Dimmed;
import org.junit.jupiter.api.Test;

/**
 * The popup asking what a new pull request says: the title starts out as given, the body empty and the draft box
 * below them unticked; Enter in the title or Ctrl+S anywhere creates it, Escape cancels.
 */
class NewPullRequestDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded()
            .openPopup(new NewPullRequestPopup(
                    new Branch.Name("feature"),
                    new Branch.Name("feature"),
                    new Branch.Name("main"),
                    new NewPullRequest("Add login", "", false)));
    // As in the app, an edit changes the model the popup shows; the other messages are only kept.
    private final NewPullRequestDialog dialog = new NewPullRequestDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof CreatePullRequestMsg.PullRequestEdited) {
            model = Update.update(model, msg).model();
        }
    });

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            SettledPilot.of(runner).press(c);
        }
        SettledPilot.of(runner).pause();
    }

    @Test
    void showsTheTitleTheBodyAndTheDraftBoxBelowThem() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Create pull request: 'feature' into 'main'"), screen);
            assertTrue(screen.contains("Add login"), screen);
            assertTrue(screen.indexOf("Title") < screen.indexOf("Body"), screen);
            assertTrue(screen.indexOf("Body") < screen.indexOf("[ ] Draft?"), screen);
            // The keys are told in the border of the popup, under the draft box.
            assertTrue(
                    screen.lines()
                            .anyMatch(line -> line.strip().startsWith("╰")
                                    && line.contains("─tab: next field  space: tick draft╯")),
                    screen);
            assertTrue(screen.indexOf("[ ] Draft?") < screen.indexOf("tab: next field"), screen);
        }
    }

    @Test
    void enterInTheTitleCreatesItAsIs() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CreatePullRequestMsg.Confirmed()), dispatched);
    }

    @Test
    void whatIsTypedAndTickedGoesToTheModel() throws Exception {
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
                Option.of(new NewPullRequest("Add login!", "Why\nit matters", true)),
                model.popup(NewPullRequestPopup.class).map(NewPullRequestPopup::pullRequest));
        assertEquals(new CreatePullRequestMsg.Confirmed(), dispatched.getLast());
    }

    @Test
    void tabGoesRoundTheFieldsWithoutLeavingThePopup() throws Exception {
        // As in the app: the panes behind the popup are focusable when they are not covered.
        java.util.function.Supplier<Element> screen =
                () -> stack(new Dimmed(panel(text("files")).id("files").focusable(), Theme.Backdrop.DIM_ONLY), view());
        java.util.List<String> focused = new ArrayList<>();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(screen::get)) {
            SettledPilot.focus(runner, NewPullRequestDialog.ID);
            RenderedText.ofLarge(runner, screen::get);
            for (int i = 0; i < 3; i++) {
                SettledPilot.of(runner).press(KeyCode.TAB);
                SettledPilot.of(runner).pause();
                focused.add(runner.runner().focusManager().focusedId());
            }
            SettledPilot.of(runner).press(KeyCode.TAB, KeyModifiers.SHIFT);
            SettledPilot.of(runner).pause();
            focused.add(runner.runner().focusManager().focusedId());
        }

        assertEquals(
                java.util.List.of(
                        NewPullRequestDialog.BODY_ID,
                        NewPullRequestDialog.DRAFT_ID,
                        NewPullRequestDialog.ID,
                        NewPullRequestDialog.DRAFT_ID),
                focused);
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
                        new CreatePullRequestMsg.Cancelled(),
                        new CreatePullRequestMsg.Cancelled(),
                        new CreatePullRequestMsg.Cancelled()),
                dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, NewPullRequestDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
