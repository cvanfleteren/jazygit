package net.vanfleteren.jazygit.feature.branch.rename;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking whether renaming a branch renames its upstream on its remote too.
 */
class RenameUpstreamDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final ChoiceDialog<RenameUpstreamPopup> dialog =
            new ChoiceDialog<>(RenameBranchDialogs.RENAME_UPSTREAM, cursor);
    private final Model open = TestModels.loaded(SampleData.git())
            .openPopup(new RenameUpstreamPopup(
                    new BranchName("feature"),
                    new BranchName("topic"),
                    new RemoteBranch("origin", new BranchName("feature"))));

    @Test
    void lettersChooseAnOption() throws Exception {
        assertEquals(
                List.of(new RenameBranchMsg.LocallyChosen()),
                pressing(r -> SettledPilot.of(r).press('l')));
        dispatched.clear();
        assertEquals(
                List.of(new RenameBranchMsg.WithUpstreamChosen()),
                pressing(r -> SettledPilot.of(r).press('r')));
    }

    @Test
    void enterRenamesOnlyLocally() throws Exception {
        assertEquals(
                List.of(new RenameBranchMsg.LocallyChosen()),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ENTER)));
    }

    @Test
    void escapeCancels() throws Exception {
        assertEquals(
                List.of(new Msg.PopupDismissed(RenameUpstreamPopup.class)),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ESCAPE)));
    }

    @Test
    void theOptionsNameBothBranchesOnTheRemote() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            String screen = RenderedText.ofLarge(runner, () -> view(open));
            assertTrue(screen.contains("Rename feature to topic"), screen);
            assertTrue(
                    screen.contains("(l) rename only the local branch, which keeps following origin/feature"), screen);
            assertTrue(screen.contains("(r) rename on the remote too: origin/feature becomes origin/topic"), screen);
        }
    }

    private List<Msg> pressing(Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            SettledPilot.focus(runner, RenameBranchDialogs.RENAME_UPSTREAM.id());
            RenderedText.of(runner, () -> view(open));
            keys.press(runner);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(cursor.on(model)).get());
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }
}
