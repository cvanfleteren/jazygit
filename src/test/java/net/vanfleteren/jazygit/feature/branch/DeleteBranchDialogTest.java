package net.vanfleteren.jazygit.feature.branch;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.Sha;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;
import static dev.tamboui.toolkit.Toolkit.*;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The popup asking where to delete a branch.
 */
class DeleteBranchDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<DeleteBranchPopup> dialog = new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(BranchDialogs.DELETE_BRANCH, dispatched::add);
    private final Model open = Update.update(TestModels.loaded(new SampleData()),
            new DeleteBranchMsg.Requested("feature/initial-layout")).model();

    @Test
    void lettersChooseAnOption() throws Exception {
        assertEquals(List.of(new DeleteBranchMsg.Chosen(DeleteScope.LOCAL)), pressing(r -> r.pilot().press('d')));
    }

    @Test
    void bDeletesLocalAndRemote() throws Exception {
        assertEquals(List.of(new DeleteBranchMsg.Chosen(DeleteScope.BOTH)), pressing(r -> r.pilot().press('b')));
    }

    @Test
    void arrowsAndEnterChooseTheSelectedLine() throws Exception {
        assertEquals(List.of(new DeleteBranchMsg.Chosen(DeleteScope.REMOTE)), pressing(r -> {
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
        }));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(List.of(new DeleteBranchMsg.Cancelled()), pressing(r -> r.pilot().press(KeyCode.ESCAPE)));
        dispatched.clear();
        assertEquals(List.of(new DeleteBranchMsg.Cancelled()), pressing(r -> r.pilot().press('c')));
    }

    @Test
    void remoteOptionsAreNotActionableForALocalOnlyBranch() throws Exception {
        Model localOnly = Update.update(TestModels.loaded(new SampleData()),
                new DeleteBranchMsg.Requested("feature/jgit-backend")).model();

        assertEquals(List.of(), pressing(localOnly, r -> {
            r.pilot().press('r');
            r.pilot().press('b');
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
        }));
        dispatched.clear();
        assertEquals(List.of(new DeleteBranchMsg.Chosen(DeleteScope.LOCAL)), pressing(localOnly, r -> r.pilot().press('d')));
    }

    @Test
    void remoteOptionsAreNotActionableForTheDefaultBranch() throws Exception {
        Model model = Update.update(TestModels.loaded(new SampleData()), new LoadMsg.BranchesLoaded(List.of(
                new Branch("x", true, new Sha("1")),
                new Branch("trunk", false, new Sha("2"), java.time.Instant.EPOCH, true, true)))).model();
        Model open = Update.update(model, new DeleteBranchMsg.Requested("trunk")).model();

        assertEquals(List.of(), pressing(open, r -> {
            r.pilot().press('r');
            r.pilot().press('b');
        }));
    }

    @Test
    void listsAllOptionsWithCancelAndTheKeyHint() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            String screen = RenderedText.ofLarge(runner, () -> view(open));
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(d) delete local branch"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(b) delete local and remote branch"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(c) Cancel"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("(enter) select   (esc) cancel"), screen);
        }
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
            runner.runner().focusManager().setFocus(BranchDialogs.DELETE_BRANCH.id());
            RenderedText.of(runner, () -> view(model));
            keys.press(runner);
            runner.pilot().pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
