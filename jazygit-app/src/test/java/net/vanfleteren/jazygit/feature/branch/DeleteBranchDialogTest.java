package net.vanfleteren.jazygit.feature.branch;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking where to delete a branch.
 */
class DeleteBranchDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<DeleteBranchPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(BranchDialogs.DELETE_BRANCH, dispatched::add);
    private final Model open = Update.update(
                    TestModels.loaded(new SampleData()),
                    new DeleteBranchMsg.Requested(new Branch.Name("feature/initial-layout")))
            .model();

    @Test
    void lettersChooseAnOption() throws Exception {
        assertEquals(
                List.of(new DeleteBranchMsg.Chosen(DeleteScope.LOCAL)),
                pressing(r -> SettledPilot.of(r).press('d')));
    }

    @Test
    void bDeletesLocalAndRemote() throws Exception {
        assertEquals(
                List.of(new DeleteBranchMsg.Chosen(DeleteScope.BOTH)),
                pressing(r -> SettledPilot.of(r).press('b')));
    }

    @Test
    void arrowsAndEnterChooseTheSelectedLine() throws Exception {
        assertEquals(List.of(new DeleteBranchMsg.Chosen(DeleteScope.REMOTE)), pressing(r -> {
            SettledPilot.of(r).press(KeyCode.DOWN);
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(
                List.of(new DeleteBranchMsg.Cancelled()),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ESCAPE)));
        dispatched.clear();
        assertEquals(
                List.of(new DeleteBranchMsg.Cancelled()),
                pressing(r -> SettledPilot.of(r).press('c')));
    }

    @Test
    void remoteOptionsAreNotActionableForALocalOnlyBranch() throws Exception {
        Model localOnly = Update.update(
                        TestModels.loaded(new SampleData()),
                        new DeleteBranchMsg.Requested(new Branch.Name("feature/jgit-backend")))
                .model();

        assertEquals(List.of(), pressing(localOnly, r -> {
            SettledPilot.of(r).press('r');
            SettledPilot.of(r).press('b');
            SettledPilot.of(r).press(KeyCode.DOWN);
            SettledPilot.of(r).press(KeyCode.ENTER);
            SettledPilot.of(r).press(KeyCode.DOWN);
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
        dispatched.clear();
        assertEquals(
                List.of(new DeleteBranchMsg.Chosen(DeleteScope.LOCAL)),
                pressing(localOnly, r -> SettledPilot.of(r).press('d')));
    }

    @Test
    void remoteOptionsAreNotActionableForTheDefaultBranch() throws Exception {
        Model model = Update.update(
                        TestModels.loaded(new SampleData()),
                        new LoadMsg.BranchesLoaded(List.of(
                                new Branch(new Branch.Name("x"), true, new Sha("1")),
                                new Branch(
                                        new Branch.Name("trunk"),
                                        false,
                                        new Sha("2"),
                                        java.time.Instant.EPOCH,
                                        true,
                                        true))))
                .model();
        Model open = Update.update(model, new DeleteBranchMsg.Requested(new Branch.Name("trunk")))
                .model();

        assertEquals(List.of(), pressing(open, r -> {
            SettledPilot.of(r).press('r');
            SettledPilot.of(r).press('b');
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

    private List<Msg> pressing(Keys keys) throws Exception {
        return pressing(open, keys);
    }

    private List<Msg> pressing(Model model, Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model))) {
            SettledPilot.focus(runner, BranchDialogs.DELETE_BRANCH.id());
            RenderedText.of(runner, () -> view(model));
            keys.press(runner);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }
}
