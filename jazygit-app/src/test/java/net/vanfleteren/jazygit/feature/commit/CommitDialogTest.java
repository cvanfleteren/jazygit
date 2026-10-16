package net.vanfleteren.jazygit.feature.commit;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The commit popup: Tab switches fields, Enter in the summary and Ctrl+S or Ctrl+Enter in either commit, Escape
 * cancels.
 */
class CommitDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CommitDialog dialog = new CommitDialog(dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        // The sample data has staged files, so the commit popup opens directly.
        Model loaded = TestModels.loaded(new SampleData());
        Model asked = Update.update(loaded, new CommitMsg.Requested()).model();
        return asked.popup(CommitPopup.class).isDefined()
                ? asked
                : Update.update(
                                asked,
                                new CommitMsg.StagedForCommit(new CommitCmd.StageForCommit(List.of()), List.of()))
                        .model();
    }

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            SettledPilot.of(runner).press(c);
        }
        SettledPilot.of(runner).pause();
    }

    @Test
    void showsBothFieldTitles() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Commit summary"), screen);
            assertTrue(screen.contains("Commit description"), screen);
        }
    }

    @Test
    void theEditorKeyIsShownInTheBottomBorderOfTheDescription() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.lines().anyMatch(line -> line.contains("─ctrl+o: open in editor")), screen);
        }
    }

    @Test
    void enterInTheSummaryCommits() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "fix it");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed(new CommitMessage("fix it", ""))), dispatched);
    }

    @Test
    void ctrlEnterInTheDescriptionCommits() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "fix it");
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "because");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "reasons");
            SettledPilot.of(runner).press(KeyCode.ENTER, KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new CommitMsg.Confirmed(new CommitMessage("fix it", "because\nreasons"))),
                dispatched);
    }

    @Test
    void ctrlSCommitsFromEitherField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+S opens the settings, after the fields had their chance.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.hasCtrl() && key.isChar('s')
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            type(runner, "fix it");
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            type(runner, "again");
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "because");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "reasons");
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(
                        new CommitMsg.Confirmed(new CommitMessage("fix it", "")),
                        new CommitMsg.Confirmed(new CommitMessage("again", "because\nreasons"))),
                dispatched);
    }

    @Test
    void escapeCancelsFromEitherField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app has a global Escape, which gets the key before the dialog around the fields does.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.code() == KeyCode.ESCAPE
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).press(KeyCode.TAB);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Cancelled(), new CommitMsg.Cancelled()), dispatched);
    }

    @Test
    void rewordStartsOutWithTheCurrentMessageAndConfirmsIt() throws Exception {
        CommitDialog reword = CommitDialog.reword(dispatched::add);
        Model rewording = Update.update(
                        TestModels.loaded(new SampleData()),
                        new RewordMsg.Requested(
                                new SampleData().commits().get().head().sha()))
                .model();
        Commit last = rewording.popup(RewordPopup.class).get().commit();

        try (ToolkitTestRunner runner =
                ToolkitTestRunner.runTest(() -> reword.render(rewording).get())) {
            SettledPilot.focus(runner, CommitDialog.REWORD_ID);
            String screen =
                    RenderedText.ofLarge(runner, () -> reword.render(rewording).get());
            assertTrue(screen.contains(last.message()), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new RewordMsg.Confirmed(new CommitMessage(last.message(), last.body()))), dispatched);
    }

    @Test
    void typingInTheRewordDialogAddsToTheEndOfTheCurrentSummary() throws Exception {
        CommitDialog reword = CommitDialog.reword(dispatched::add);
        Model rewording = Update.update(
                        TestModels.loaded(new SampleData()),
                        new RewordMsg.Requested(
                                new SampleData().commits().get().head().sha()))
                .model();
        Commit last = rewording.popup(RewordPopup.class).get().commit();

        try (ToolkitTestRunner runner =
                ToolkitTestRunner.runTest(() -> reword.render(rewording).get())) {
            SettledPilot.focus(runner, CommitDialog.REWORD_ID);
            RenderedText.ofLarge(runner, () -> reword.render(rewording).get());
            SettledPilot.of(runner).press('!');
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new RewordMsg.Confirmed(new CommitMessage(last.message() + "!", last.body()))),
                dispatched);
    }

    @Test
    void ctrlOOpensTheMessageInTheEditorFromEitherField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "fix it");
            SettledPilot.of(runner).press('o', KeyModifiers.CTRL);
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "because");
            SettledPilot.of(runner).press('o', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(
                        new MessageEditorMsg.Requested(new CommitMessage("fix it", "")),
                        new MessageEditorMsg.Requested(new CommitMessage("fix it", "because"))),
                dispatched);
    }

    @Test
    void theMessageFromTheEditorReplacesWhatWasTyped() throws Exception {
        Model[] model = {open};
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(
                () -> stack(text("background"), dialog.render(model[0]).get()))) {
            SettledPilot.focus(runner, CommitDialog.ID);
            RenderedText.of(runner, () -> dialog.render(model[0]).get());
            type(runner, "draft");
            model[0] = Update.update(
                            model[0], new MessageEditorMsg.Edited(new CommitMessage("edited", "at length"), List.of()))
                    .model();
            String screen =
                    RenderedText.ofLarge(runner, () -> dialog.render(model[0]).get());
            assertTrue(screen.contains("at length"), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed(new CommitMessage("edited", "at length"))), dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(CommitPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, CommitDialog.ID);
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
