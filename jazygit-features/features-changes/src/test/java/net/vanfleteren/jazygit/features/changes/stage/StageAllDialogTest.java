package net.vanfleteren.jazygit.features.changes.stage;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.features.changes.commit.create.CommitMsg;
import net.vanfleteren.jazygit.features.changes.commit.create.StageAllPopup;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking whether to stage all files: Enter confirms, Escape cancels.
 */
class StageAllDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog<
                    net.vanfleteren.jazygit.features.changes.commit.create.StageAllPopup>
            dialog = new net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog<>(
                    net.vanfleteren.jazygit.features.changes.commit.create.CreateCommitDialogs.STAGE_ALL,
                    dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Model unstaged = Update.update(
                        TestModels.loaded(SampleData.git()),
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                                List.of(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();
        return Update.update(unstaged, new CommitMsg.Requested()).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommitMsg.StageAllConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PopupMsg.Dismissed(StageAllPopup.class)), dispatched);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded(SampleData.git())).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(StageAllPopup.class).isDefined(), "nothing is staged");
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(
                runner, net.vanfleteren.jazygit.features.changes.commit.create.CreateCommitDialogs.STAGE_ALL.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
