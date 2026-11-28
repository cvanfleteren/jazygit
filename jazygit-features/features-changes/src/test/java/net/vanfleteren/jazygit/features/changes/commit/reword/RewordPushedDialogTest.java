package net.vanfleteren.jazygit.features.changes.commit.reword;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedModels;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The warning before rewording a commit that is pushed already (#722): Enter goes on to the message, Escape cancels.
 */
class RewordPushedDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<RewordPushedPopup> dialog = new ConfirmDialog<>(RewordDialogs.PUSHED, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Commit last = Fixtures.commit(new Sha("c2"), "Ada", "ada@example.com", Instant.EPOCH, "Last", "");
        Model model = Update.update(TestModels.loaded(), TestModels.commitsLoaded(List.of(last)))
                .model();
        return Update.update(model, new RewordMsg.Checked(last.sha(), Option.of(PushedModels.warning(false))))
                .model();
    }

    @Test
    void showsTheWarningAndAsksWhetherToGoOn() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view).replaceAll("[│\\s]+", " ");
            assertTrue(screen.contains("Rewrite a pushed commit"), screen);
            assertTrue(
                    screen.contains("This commit is already on origin/feature; the next push has to be a force push."),
                    screen);
            assertTrue(screen.contains("Go on anyway?"), screen);
        }
    }

    @Test
    void enterGoesOn() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new RewordMsg.PushedConfirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PopupMsg.Dismissed(RewordPushedPopup.class)), dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(RewordPushedPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, RewordDialogs.PUSHED.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
