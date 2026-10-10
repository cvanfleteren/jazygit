package net.vanfleteren.jazygit.feature.commit;


import net.vanfleteren.jazygit.ui.RenderedText;

import net.vanfleteren.jazygit.feature.commit.AmendMsg;
import net.vanfleteren.jazygit.state.LoadMsg;
import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The popup asking to confirm amending the last commit: Enter confirms, Escape cancels.
 */
class AmendDialogTest {

    private final List<Msg> dispatched = new ArrayList<>();
    private final AmendDialog dialog = new AmendDialog(dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Model unstaged = Update.update(TestModels.loaded(new SampleData()), new LoadMsg.StatusLoaded(
                new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.MODIFIED))))).model();
        return Update.update(unstaged, new AmendMsg.Requested()).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(List.of(new AmendMsg.Confirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(List.of(new AmendMsg.Cancelled()), dispatched);
    }

    @Test
    void showsTitleAndExplanation() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Amend last commit"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Are you sure you want to amend the last commit?"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("commits panel."), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded(new SampleData())).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertEquals(true, open.popup(AmendPopup.class).isPresent());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(AmendDialog.ID);
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).orElseThrow());
    }
}
