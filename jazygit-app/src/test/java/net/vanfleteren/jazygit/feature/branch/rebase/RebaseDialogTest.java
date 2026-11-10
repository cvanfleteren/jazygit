package net.vanfleteren.jazygit.feature.branch.rebase;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PopupMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking how to rebase the checked out branch.
 */
class RebaseDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<RebasePopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(RebaseDialogs.REBASE, dispatched::add);
    private Model open = Update.update(
                    TestModels.loaded(SampleData.git()),
                    new RebaseMsg.Requested(new BranchName("feature/initial-layout")))
            .model();

    @Test
    void sChoosesASimpleRebase() throws Exception {
        assertEquals(
                List.of(new RebaseMsg.Chosen(RebaseMsg.Target.ONTO)),
                pressing(r -> SettledPilot.of(r).press('s')));
    }

    @Test
    void enterChoosesTheFirstLine() throws Exception {
        assertEquals(
                List.of(new RebaseMsg.Chosen(RebaseMsg.Target.ONTO)),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ENTER)));
    }

    @Test
    void escapeCancels() throws Exception {
        assertEquals(
                List.of(new PopupMsg.Dismissed(RebasePopup.class)),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ESCAPE)));
    }

    @Test
    void titleNamesTheCheckedOutBranchAndTheOptionTheOtherOne() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("Rebase 'main'"), screen);
            assertTrue(screen.contains("(s) simple rebase onto feature/initial-layout"), screen);
        }
    }

    @Test
    void bChoosesTheBaseBranchWhenThereIsOne() throws Exception {
        open = withBase(new BranchName("feature/jgit-backend"));
        assertEquals(
                List.of(new RebaseMsg.Chosen(RebaseMsg.Target.BASE)),
                pressing(r -> SettledPilot.of(r).press('b')));
    }

    @Test
    void enterOnTheCheckedOutRowChoosesTheBase() throws Exception {
        open = open.openPopup(new RebasePopup(
                new BranchName("main"), new BranchName("main"), Option.of(new BranchName("feature/jgit-backend"))));
        assertEquals(
                List.of(new RebaseMsg.Chosen(RebaseMsg.Target.BASE)),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ENTER)));
    }

    @Test
    void onTheCheckedOutRowWhatRebasingOnTheBaseDoesShowsFirst() throws Exception {
        open = open.openPopup(new RebasePopup(
                new BranchName("main"), new BranchName("main"), Option.of(new BranchName("feature/jgit-backend"))));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("on top of its base branch"), screen);
            assertFalse(screen.contains("on top of the highlighted branch"), screen);
        }
    }

    @Test
    void elsewhereWhatASimpleRebaseDoesShowsFirst() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("on top of the highlighted branch"), screen);
            assertFalse(screen.contains("on top of its base branch"), screen);
        }
    }

    @Test
    void theBaseBranchIsNamed() throws Exception {
        open = withBase(new BranchName("feature/jgit-backend"));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("(b) rebase on base branch (feature/jgit-backend)"), screen);
        }
    }

    @Test
    void onTheCheckedOutRowNeitherAppliesWithoutABaseButBothShow() throws Exception {
        open = Update.update(TestModels.loaded(SampleData.git()), new RebaseMsg.Requested(new BranchName("main")))
                .model();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("(s) simple rebase onto main"), screen);
            assertTrue(screen.contains("(b) rebase on base branch (none)"), screen);
        }
        dispatched.clear();
        assertEquals(List.of(), pressing(r -> {
            SettledPilot.of(r).press('s');
            SettledPilot.of(r).press('b');
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
    }

    /**
     * The popup as opened on {@code feature/initial-layout}, with {@code base} as the base of {@code main}.
     */
    private Model withBase(BranchName base) {
        return open.openPopup(
                new RebasePopup(new BranchName("main"), new BranchName("feature/initial-layout"), Option.of(base)));
    }

    private List<Msg> pressing(Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            SettledPilot.focus(runner, RebaseDialogs.REBASE.id());
            RenderedText.of(runner, () -> view());
            keys.press(runner);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }
}
