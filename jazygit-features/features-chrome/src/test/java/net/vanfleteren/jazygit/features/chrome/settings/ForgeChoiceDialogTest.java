package net.vanfleteren.jazygit.features.chrome.settings;

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
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.CursorFeedback;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeCandidate;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeChoicePopup;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeMsg;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * Asking which forge the repository is on (ADR 177).
 */
class ForgeChoiceDialogTest {

    private static final ForgeCandidate GITHUB =
            new ForgeCandidate("github", "GitHub", new RemoteName("origin"), "https://github.com/cvf/nix.git");
    private static final ForgeCandidate FORGEJO =
            new ForgeCandidate("forgejo", "Forgejo", new RemoteName("codeberg"), "https://codeberg.org/cvf/nix.git");

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final ChoiceDialog<ForgeChoicePopup> dialog = new ChoiceDialog<>(ForgeChoiceDialog.SPEC, cursor);
    private final Model open =
            TestModels.loaded().openPopup(new ForgeChoicePopup(List.of(GITHUB, FORGEJO), Option.of(FORGEJO)));

    @Test
    void itListsEachRemoteByItsForgeAndNoForge() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Which forge is this repository on?"), screen);
            assertTrue(screen.contains("(1) GitHub: origin (https://github.com/cvf/nix.git)"), screen);
            assertTrue(screen.contains("(2) Forgejo: codeberg (https://codeberg.org/cvf/nix.git)"), screen);
            assertTrue(screen.contains("(n) No forge"), screen);
            assertFalse(screen.contains("(3)"), screen);
        }
    }

    @Test
    void itOpensAtTheRulesPickAndEscapePutsItOff() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            SettledPilot.focus(runner, ForgeChoiceDialog.SPEC.id());
            RenderedText.of(runner, this::view);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(List.of(new ForgeMsg.Picked(1), new ForgeMsg.Later()), List.ofAll(dispatched));
    }

    @Test
    void aDigitOrNChooses() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            SettledPilot.focus(runner, ForgeChoiceDialog.SPEC.id());
            RenderedText.of(runner, this::view);
            SettledPilot.of(runner).press('1');
            SettledPilot.of(runner).press('n');
            SettledPilot.of(runner).pause();
        }

        assertEquals(List.of(new ForgeMsg.Picked(0), new ForgeMsg.Chosen(Option.none())), List.ofAll(dispatched));
    }

    private Element view() {
        return stack(text("background"), dialog.render(cursor.on(open)).get());
    }
}
