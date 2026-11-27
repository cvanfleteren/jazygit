package net.vanfleteren.jazygit.features.branches.branch.pull;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.git.model.PullMode;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking how to pull a diverged branch.
 */
class PullDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<PullPopup> dialog = new ChoiceDialog<>(PullDialogs.PULL, dispatched::add);
    private final Model open = PopupUpdate.ask(
                    TestModels.loaded(), new PullPopup(new net.vanfleteren.jazygit.core.repo.BranchName("main")))
            .model();

    @Test
    void offersToRebaseOrMergeWithWhatEachDoes() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Pull 'main', which has diverged from its upstream"), screen);
            assertTrue(screen.contains("(r) Pull by rebasing"), screen);
            assertTrue(screen.contains("(m) Pull by merging"), screen);
        }
    }

    @Test
    void eachLetterChoosesItsWayToPull() throws Exception {
        assertEquals(List.of(new PullMsg.Chosen(PullMode.REBASE)), pressing('r'));
        assertEquals(List.of(new PullMsg.Chosen(PullMode.MERGE)), pressing('m'));
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }
        assertEquals(java.util.List.of(new PopupMsg.Dismissed(PullPopup.class)), dispatched);
    }

    private List<Msg> pressing(char key) throws Exception {
        dispatched.clear();
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(key);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(PullPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, PullDialogs.PULL.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
