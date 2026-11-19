package net.vanfleteren.jazygit.app;

import static net.vanfleteren.jazygit.app.TestApp.await;
import static net.vanfleteren.jazygit.app.TestApp.awaitModel;
import static net.vanfleteren.jazygit.app.TestApp.focusedId;
import static net.vanfleteren.jazygit.app.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.app.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.control.Option;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.app.askpass.SecretBuffer;
import net.vanfleteren.jazygit.state.popup.SshPromptPopup;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * ssh asking the user through the app (ADR 150): its popup takes the focus and the keys, and gives the focus back once
 * it is answered.
 */
class JazygitAppSshPromptTest {

    private static final String PROMPT = "Enter passphrase for key '/home/u/.ssh/id_ed25519': ";

    @Test
    void thePopupTakesTheKeysAndEnterAnswers() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            String before = onRenderThread(testRunner, () -> focusedId(testRunner));

            CompletableFuture<Option<SecretBuffer>> answer = app.asking().ask(1, PROMPT);
            awaitModel(
                    testRunner, app, model -> model.popup(SshPromptPopup.class).isDefined());
            await(
                    () -> "ssh-prompt".equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the popup focused");
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('q');
            pilot.press('?');
            pilot.press(KeyCode.ENTER);

            assertArrayEquals(
                    "q?".getBytes(StandardCharsets.UTF_8),
                    answer.get(TestApp.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                            .get()
                            .utf8());
            awaitModel(testRunner, app, model -> model.popup().isEmpty());
            await(() -> before.equals(onRenderThread(testRunner, () -> focusedId(testRunner))), "the focus back");
            assertEquals(0, app.quits.get());
        } finally {
            app.onStop();
        }
    }
}
