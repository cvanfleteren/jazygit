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
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.commit.create.CreateCommitCmd;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordPopup;
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
    // The message the popup held when each of the dispatched messages was sent.
    private final java.util.List<CommitMessage> held = new ArrayList<>();
    private Model open = openModel();
    private final CommitDialog dialog = new CommitDialog(this::dispatch);

    /**
     * As in the app, an edit changes the model the popup shows; the other messages are only kept, with the message
     * the popup held then.
     */
    private void dispatch(Msg msg) {
        if (msg instanceof MessageEdited) {
            open = Update.update(open, msg).model();
        } else {
            dispatched.add(msg);
            held.add(open.popup(MessagePopup.class).map(MessagePopup::message).getOrNull());
        }
    }

    private static Model openModel() {
        // The sample data has staged files, so the commit popup opens directly.
        Model loaded = TestModels.loaded(new SampleData());
        Model asked = Update.update(loaded, new CommitMsg.Requested()).model();
        return asked.popup(CommitPopup.class).isDefined()
                ? asked
                : Update.update(
                                asked,
                                new CommitMsg.StagedForCommit(new CreateCommitCmd.StageForCommit(List.of()), List.of()))
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

        assertEquals(java.util.List.of(new CommitMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("fix it", "")), held);
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

        assertEquals(java.util.List.of(new CommitMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("fix it", "because\nreasons")), held);
    }

    @Test
    void theReadlineKeysEditEitherField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "fix the bug");
            SettledPilot.of(runner).press('w', KeyModifiers.CTRL);
            type(runner, "typo");
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "because");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "of the reasons");
            SettledPilot.of(runner).press('b', KeyModifiers.of(false, true, false));
            SettledPilot.of(runner).press('u', KeyModifiers.CTRL);
            type(runner, "for ");
            SettledPilot.of(runner).press(KeyCode.ENTER, KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("fix the typo", "because\nfor reasons")), held);
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
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "because");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "reasons");
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed(), new CommitMsg.Confirmed()), dispatched);
        assertEquals(
                java.util.List.of(new CommitMessage("fix it", ""), new CommitMessage("fix it", "because\nreasons")),
                held);
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
    void upInTheSummaryFillsInTheSummariesOfEarlierCommits() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "draft");
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("Scaffold Maven project", "")), held);
    }

    @Test
    void downInTheSummaryBringsBackWhatWasTyped() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "draft");
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("draft", "")), held);
    }

    @Test
    void upInTheRewordDialogSkipsTheSummaryOfTheCommitItself() throws Exception {
        CommitDialog reword = CommitDialog.reword(this::dispatch);
        open = Update.update(
                        TestModels.loaded(new SampleData()),
                        new RewordMsg.Requested(
                                new SampleData().commits().get().head().sha()))
                .model();
        Commit last = open.popup(RewordPopup.class).get().commit();

        try (ToolkitTestRunner runner =
                ToolkitTestRunner.runTest(() -> reword.render(open).get())) {
            SettledPilot.focus(runner, CommitDialog.REWORD_ID);
            RenderedText.ofLarge(runner, () -> reword.render(open).get());
            SettledPilot.of(runner).press(KeyCode.UP);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new RewordMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("Scaffold Maven project", last.body())), held);
    }

    @Test
    void rewordStartsOutWithTheCurrentMessageAndConfirmsIt() throws Exception {
        CommitDialog reword = CommitDialog.reword(this::dispatch);
        open = Update.update(
                        TestModels.loaded(new SampleData()),
                        new RewordMsg.Requested(
                                new SampleData().commits().get().head().sha()))
                .model();
        Commit last = open.popup(RewordPopup.class).get().commit();

        try (ToolkitTestRunner runner =
                ToolkitTestRunner.runTest(() -> reword.render(open).get())) {
            SettledPilot.focus(runner, CommitDialog.REWORD_ID);
            String screen =
                    RenderedText.ofLarge(runner, () -> reword.render(open).get());
            assertTrue(screen.contains(last.message()), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new RewordMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage(last.message(), last.body())), held);
    }

    @Test
    void typingInTheRewordDialogAddsToTheEndOfTheCurrentSummary() throws Exception {
        CommitDialog reword = CommitDialog.reword(this::dispatch);
        open = Update.update(
                        TestModels.loaded(new SampleData()),
                        new RewordMsg.Requested(
                                new SampleData().commits().get().head().sha()))
                .model();
        Commit last = open.popup(RewordPopup.class).get().commit();

        try (ToolkitTestRunner runner =
                ToolkitTestRunner.runTest(() -> reword.render(open).get())) {
            SettledPilot.focus(runner, CommitDialog.REWORD_ID);
            RenderedText.ofLarge(runner, () -> reword.render(open).get());
            SettledPilot.of(runner).press('!');
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new RewordMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage(last.message() + "!", last.body())), held);
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

        assertEquals(java.util.List.of(new MessageEditorMsg.Requested(), new MessageEditorMsg.Requested()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("fix it", ""), new CommitMessage("fix it", "because")), held);
    }

    @Test
    void theMessageFromTheEditorReplacesWhatWasTyped() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "draft");
            open = Update.update(open, new MessageEditorMsg.Edited(new CommitMessage("edited", "at length"), List.of()))
                    .model();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("at length"), screen);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.Confirmed()), dispatched);
        assertEquals(java.util.List.of(new CommitMessage("edited", "at length")), held);
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
