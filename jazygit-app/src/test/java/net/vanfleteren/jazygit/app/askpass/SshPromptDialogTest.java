package net.vanfleteren.jazygit.app.askpass;

import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

/**
 * The popup in which ssh asks for a secret shows what ssh asks, and a dot for each character typed, never the
 * characters (ADR 150).
 */
class SshPromptDialogTest {

    private static final String PROMPT = "Enter passphrase for key '/home/u/.ssh/id_ed25519': ";

    @Test
    void itShowsWhatSshAsksAndADotForEachCharacter() throws Exception {
        List<Msg> sent = new ArrayList<>();
        PopupAsking asking = new PopupAsking(Runnable::run, sent::add, asKeys -> {});
        SshPromptDialog dialog = (SshPromptDialog) SshPromptDialog.slot(asking).view();
        asking.ask(1, PROMPT);
        Model model = Update.update(TestModels.loaded(), sent.getFirst()).model();
        SshPromptDialog.SecretInput input = new SshPromptDialog.SecretInput(asking, SshPromptDialog.ID);
        "s3cr3t".chars().forEach(c -> input.handleKeyEvent(KeyEvent.ofChar((char) c), true));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> text(""))) {
            String shown =
                    RenderedText.ofLarge(testRunner, () -> dialog.render(model).get());

            assertTrue(shown.contains("ssh asks"), shown);
            assertTrue(shown.contains("Enter passphrase for key '/home/u/.ssh/id_ed25519':"), shown);
            assertTrue(shown.contains("••••••"), shown);
            assertFalse(shown.contains("•••••••"), shown);
            assertFalse(shown.contains("s3cr3t"), shown);
        }
        assertEquals(List.of(SshPromptDialog.ID), dialog.ids().toJavaList());
    }

    @Test
    void itIsClosedWhileSshAsksNothing() {
        SshPromptDialog dialog =
                (SshPromptDialog) SshPromptDialog.slot(new PopupAsking(Runnable::run, msg -> {}, asKeys -> {}))
                        .view();

        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }
}
