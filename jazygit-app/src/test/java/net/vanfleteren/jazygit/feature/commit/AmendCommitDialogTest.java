package net.vanfleteren.jazygit.feature.commit;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popups asking to confirm amending a commit, and whether to amend it with all files when none is staged:
 * Enter confirms, Escape cancels.
 */
class AmendCommitDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<AmendCommitPopup> dialog =
            new ConfirmDialog<>(CommitDialogs.AMEND_COMMIT, dispatched::add);
    private final ConfirmDialog<AmendCommitStageAllPopup> stageAll =
            new ConfirmDialog<>(CommitDialogs.AMEND_COMMIT_STAGE_ALL, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Model loaded = TestModels.loaded(new SampleData());
        Commit commit = loaded.repo().commits().toOption().get().head();
        Model unstaged = Update.update(
                        loaded,
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(new Branch.Name("main"), commit.sha()),
                                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();
        return Update.update(unstaged, new AmendCommitMsg.Requested(commit.sha()))
                .model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_COMMIT.id(), this::view)) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new AmendCommitMsg.Confirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_COMMIT.id(), this::view)) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new AmendCommitMsg.Cancelled()), dispatched);
    }

    @Test
    void showsTitleAndQuestion() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_COMMIT.id(), this::view)) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Amend commit"), screen);
            assertTrue(screen.contains("Are you sure you want to amend this commit with your"), screen);
            assertTrue(screen.contains("staged changes?"), screen);
        }
    }

    @Test
    void stageAllEnterConfirmsAndEscapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_COMMIT_STAGE_ALL.id(), this::stageAllView)) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_COMMIT_STAGE_ALL.id(), this::stageAllView)) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new AmendCommitMsg.StageAllConfirmed(), new AmendCommitMsg.Cancelled()), dispatched);
    }

    @Test
    void stageAllShowsTitleAndQuestion() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_COMMIT_STAGE_ALL.id(), this::stageAllView)) {
            String screen = RenderedText.ofLarge(runner, this::stageAllView);
            assertTrue(screen.contains("No files staged"), screen);
            assertTrue(screen.contains("You have not staged any files. Commit all files?"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded(new SampleData())).isEmpty());
        assertTrue(stageAll.render(open).isEmpty());
    }

    private ToolkitTestRunner start(String id, java.util.function.Supplier<Element> view) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(view::get);
        SettledPilot.focus(runner, id);
        RenderedText.of(runner, view::get);
        return runner;
    }

    private Element view() {
        assertTrue(open.popup(AmendCommitPopup.class).isDefined());
        return stack(text("background"), dialog.render(open).get());
    }

    private Element stageAllView() {
        Model second = Update.update(open, new AmendCommitMsg.Confirmed()).model();
        return stack(text("background"), stageAll.render(second).get());
    }
}
