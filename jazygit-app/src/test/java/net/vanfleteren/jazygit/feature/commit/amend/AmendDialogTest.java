package net.vanfleteren.jazygit.feature.commit.amend;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.commit.CommitDialogs;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popups asking to confirm amending the last or another commit, and whether to amend it with all files when
 * none is staged: Enter confirms, Escape cancels.
 */
class AmendDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<AmendPopup> dialog = new ConfirmDialog<>(CommitDialogs.AMEND, dispatched::add);
    private final ConfirmDialog<AmendStageAllPopup> stageAll =
            new ConfirmDialog<>(CommitDialogs.AMEND_STAGE_ALL, dispatched::add);
    private final Model unstaged = unstagedModel();

    /**
     * The sample commits, with HEAD at the first one and a file that is not staged.
     */
    private static Model unstagedModel() {
        Model loaded = TestModels.loaded(new SampleData());
        Commit head = loaded.repo().commits().toOption().get().head();
        return Update.update(
                        loaded,
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                Head.onBranch(new Branch.Name("main"), head.sha()),
                                List.of(new FileEntry("a.txt", ChangeType.MODIFIED)))))
                .model();
    }

    private Model asked(Option<Sha> sha) {
        Model open = Update.update(unstaged, new AmendMsg.Requested(sha)).model();
        assertTrue(open.popup(AmendPopup.class).isDefined());
        return open;
    }

    private Model older() {
        Commit older = unstaged.repo()
                .commits()
                .toOption()
                .get()
                .tail()
                .find(commit -> !commit.isMerge())
                .get();
        return asked(Option.of(older.sha()));
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND.id(), () -> view(asked(Option.none())))) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new AmendMsg.Confirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND.id(), () -> view(asked(Option.none())))) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new AmendMsg.Cancelled()), dispatched);
    }

    @Test
    void theLastCommitShowsTitleAndExplanation() throws Exception {
        Supplier<Element> view = () -> view(asked(Option.none()));
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND.id(), view)) {
            String screen = RenderedText.ofLarge(runner, view::get);
            assertTrue(screen.contains("Amend last commit"), screen);
            assertTrue(screen.contains("Are you sure you want to amend the last commit?"), screen);
            assertTrue(screen.contains("commits panel."), screen);
        }
    }

    @Test
    void anOlderCommitShowsTitleAndQuestion() throws Exception {
        Supplier<Element> view = () -> view(older());
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND.id(), view)) {
            String screen = RenderedText.ofLarge(runner, view::get);
            assertTrue(screen.contains("Amend commit"), screen);
            assertTrue(screen.contains("Are you sure you want to amend this commit with your"), screen);
            assertTrue(screen.contains("staged changes?"), screen);
        }
    }

    @Test
    void stageAllEnterConfirmsAndEscapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_STAGE_ALL.id(), this::stageAllView)) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_STAGE_ALL.id(), this::stageAllView)) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new AmendMsg.StageAllConfirmed(), new AmendMsg.Cancelled()), dispatched);
    }

    @Test
    void stageAllShowsTitleAndQuestion() throws Exception {
        try (ToolkitTestRunner runner = start(CommitDialogs.AMEND_STAGE_ALL.id(), this::stageAllView)) {
            String screen = RenderedText.ofLarge(runner, this::stageAllView);
            assertTrue(screen.contains("No files staged"), screen);
            assertTrue(screen.contains("You have not staged any files. Commit all files?"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded(new SampleData())).isEmpty());
        assertTrue(stageAll.render(asked(Option.none())).isEmpty());
    }

    private ToolkitTestRunner start(String id, Supplier<Element> view) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(view::get);
        SettledPilot.focus(runner, id);
        RenderedText.of(runner, view::get);
        return runner;
    }

    private Element view(Model open) {
        return stack(text("background"), dialog.render(open).get());
    }

    private Element stageAllView() {
        Model second =
                Update.update(asked(Option.none()), new AmendMsg.Confirmed()).model();
        return stack(text("background"), stageAll.render(second).get());
    }
}
