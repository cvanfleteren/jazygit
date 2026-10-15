package net.vanfleteren.jazygit.feature.branch;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking how to rebase the checked out branch.
 */
class RebaseDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<RebasePopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(BranchDialogs.REBASE, dispatched::add);
    private final Model open = Update.update(
                    TestModels.loaded(new SampleData()),
                    new RebaseMsg.Requested(new Branch.Name("feature/initial-layout")))
            .model();

    @Test
    void sChoosesASimpleRebase() throws Exception {
        assertEquals(List.of(new RebaseMsg.Chosen()), pressing(r -> r.pilot().press('s')));
    }

    @Test
    void enterChoosesTheFirstLine() throws Exception {
        assertEquals(List.of(new RebaseMsg.Chosen()), pressing(r -> r.pilot().press(KeyCode.ENTER)));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(List.of(new RebaseMsg.Cancelled()), pressing(r -> r.pilot().press(KeyCode.ESCAPE)));
        dispatched.clear();
        assertEquals(List.of(new RebaseMsg.Cancelled()), pressing(r -> r.pilot().press('c')));
    }

    @Test
    void titleNamesTheCheckedOutBranchAndTheOptionTheOtherOne() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("Rebase 'main'"), screen);
            assertTrue(screen.contains("(s) simple rebase onto feature/initial-layout"), screen);
            assertTrue(screen.contains("(c) Cancel"), screen);
        }
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }

    private List<Msg> pressing(Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            runner.runner().focusManager().setFocus(BranchDialogs.REBASE.id());
            RenderedText.of(runner, () -> view());
            keys.press(runner);
            runner.pilot().pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
