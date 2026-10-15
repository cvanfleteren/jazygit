package net.vanfleteren.jazygit.feature.stage;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.commit.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.StageAllPopup;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

/**
 * The popup asking whether to stage all files: Enter confirms, Escape cancels.
 */
class StageAllDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<net.vanfleteren.jazygit.feature.commit.StageAllPopup>
            dialog = new net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<>(
                    net.vanfleteren.jazygit.feature.commit.CommitDialogs.STAGE_ALL, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Model unstaged = Update.update(
                        TestModels.loaded(new SampleData()),
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
                                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();
        return Update.update(unstaged, new CommitMsg.Requested()).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.StageAllConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.StageAllCancelled()), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertEquals(true, dialog.render(TestModels.loaded(new SampleData())).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertEquals(true, open.popup(StageAllPopup.class).isDefined(), "nothing is staged");
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(net.vanfleteren.jazygit.feature.commit.CommitDialogs.STAGE_ALL.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
