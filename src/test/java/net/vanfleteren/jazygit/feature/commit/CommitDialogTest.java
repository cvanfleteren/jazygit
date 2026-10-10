package net.vanfleteren.jazygit.feature.commit;


import net.vanfleteren.jazygit.ui.RenderedText;

import net.vanfleteren.jazygit.git.model.Commit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commit popup: Tab switches fields, Enter in the summary and Ctrl+Enter in the description
 * commit, Escape cancels.
 */
class CommitDialogTest {

    private final List<Msg> dispatched = new ArrayList<>();
    private final CommitDialog dialog = new CommitDialog(dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        // The sample data has staged files, so the commit popup opens directly.
        Model loaded = TestModels.loaded(new SampleData());
        Model asked = Update.update(loaded, new CommitMsg.Requested()).model();
        return asked.popup(CommitPopup.class).isPresent() ? asked : Update.update(asked, new CommitMsg.StagedForCommit(List.of())).model();
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
    void enterInTheSummaryCommits() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "fix it");
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(List.of(new CommitMsg.Confirmed("fix it", "")), dispatched);
    }

    @Test
    void ctrlEnterInTheDescriptionCommits() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            type(runner, "fix it");
            runner.pilot().press(KeyCode.TAB);
            type(runner, "because");
            runner.pilot().press(KeyCode.ENTER);
            type(runner, "reasons");
            runner.pilot().press(KeyCode.ENTER, KeyModifiers.CTRL);
            runner.pilot().pause();
        }

        assertEquals(List.of(new CommitMsg.Confirmed("fix it", "because\nreasons")), dispatched);
    }

    @Test
    void escapeCancelsFromEitherField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().press(KeyCode.TAB);
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(List.of(new CommitMsg.Cancelled(), new CommitMsg.Cancelled()), dispatched);
    }

    @Test
    void rewordStartsOutWithTheCurrentMessageAndConfirmsIt() throws Exception {
        CommitDialog reword = CommitDialog.reword(dispatched::add);
        Model rewording = Update.update(TestModels.loaded(new SampleData()), new RewordMsg.Requested(0)).model();
        Commit last = rewording.popup(RewordPopup.class).orElseThrow().last();

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> reword.render(rewording).orElseThrow())) {
            runner.runner().focusManager().setFocus(CommitDialog.REWORD_ID);
            String screen = RenderedText.ofLarge(runner, () -> reword.render(rewording).orElseThrow());
            assertTrue(screen.contains(last.message()), screen);
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(List.of(new RewordMsg.Confirmed(last.message(), last.body())), dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(CommitPopup.class).isPresent());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(CommitDialog.ID);
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).orElseThrow());
    }

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            runner.pilot().press(c);
        }
        runner.pilot().pause();
    }
}
