package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.DeleteScope;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The popup asking where to delete a branch.
 */
class DeleteBranchDialogTest {

    private final List<Msg> dispatched = new ArrayList<>();
    private final DeleteBranchDialog dialog = new DeleteBranchDialog(dispatched::add);
    private final Model open = Update.update(TestModels.loaded(new SampleData()),
            new Msg.DeleteBranchRequested("feature/initial-layout")).model();

    @Test
    void lettersChooseAnOption() throws Exception {
        assertEquals(List.of(new Msg.DeleteBranchChosen(DeleteScope.LOCAL)), pressing(r -> r.pilot().press('d')));
    }

    @Test
    void bDeletesLocalAndRemote() throws Exception {
        assertEquals(List.of(new Msg.DeleteBranchChosen(DeleteScope.BOTH)), pressing(r -> r.pilot().press('b')));
    }

    @Test
    void arrowsAndEnterChooseTheSelectedLine() throws Exception {
        assertEquals(List.of(new Msg.DeleteBranchChosen(DeleteScope.REMOTE)), pressing(r -> {
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
        }));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(List.of(new Msg.DeleteBranchCancelled()), pressing(r -> r.pilot().press(KeyCode.ESCAPE)));
        dispatched.clear();
        assertEquals(List.of(new Msg.DeleteBranchCancelled()), pressing(r -> r.pilot().press('c')));
    }

    @Test
    void remoteOptionsAreNotActionableForALocalOnlyBranch() throws Exception {
        Model localOnly = Update.update(TestModels.loaded(new SampleData()),
                new Msg.DeleteBranchRequested("feature/jgit-backend")).model();

        assertEquals(List.of(), pressing(localOnly, r -> {
            r.pilot().press('r');
            r.pilot().press('b');
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
        }));
        dispatched.clear();
        assertEquals(List.of(new Msg.DeleteBranchChosen(DeleteScope.LOCAL)), pressing(localOnly, r -> r.pilot().press('d')));
    }

    @Test
    void remoteOptionsAreNotActionableForTheDefaultBranch() throws Exception {
        Model model = Update.update(TestModels.loaded(new SampleData()), new Msg.BranchesLoaded(List.of(
                new net.vanfleteren.jazygit.model.Branch("x", true, "1"),
                new net.vanfleteren.jazygit.model.Branch("trunk", false, "2", java.time.Instant.EPOCH, true, true)))).model();
        Model open = Update.update(model, new Msg.DeleteBranchRequested("trunk")).model();

        assertEquals(List.of(), pressing(open, r -> {
            r.pilot().press('r');
            r.pilot().press('b');
        }));
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }

    private List<Msg> pressing(Keys keys) throws Exception {
        return pressing(open, keys);
    }

    private List<Msg> pressing(Model model, Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model))) {
            runner.runner().focusManager().setFocus(DeleteBranchDialog.ID);
            RenderedText.of(runner, () -> view(model));
            keys.press(runner);
            runner.pilot().pause();
        }
        return List.copyOf(dispatched);
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).orElseThrow());
    }
}
