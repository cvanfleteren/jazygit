package net.vanfleteren.jazygit.app.askpass;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.control.Option;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.vanfleteren.jazygit.core.askpass.SecretBuffer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.SshPromptMsg;
import org.junit.jupiter.api.Test;

/**
 * The popup in which the user answers what ssh asks (ADR 150): the model only learns that it opens and closes, what is
 * typed stays in the popup's buffer, and a paste comes as keys while it is open.
 */
class PopupAskingTest {

    private static final String PROMPT = "Enter passphrase for key '/home/u/.ssh/id_ed25519': ";

    private final List<Msg> sent = new ArrayList<>();
    private final List<Boolean> pastingAsKeys = new ArrayList<>();
    private final PopupAsking asking = new PopupAsking(Runnable::run, sent::add, pastingAsKeys::add);
    private final SshPromptDialog.SecretInput input = new SshPromptDialog.SecretInput(asking, SshPromptDialog.ID);

    @Test
    void enterAnswersWithWhatWasTyped() {
        CompletableFuture<Option<SecretBuffer>> answer = asking.ask(1, PROMPT);
        "secrex".chars().forEach(c -> input.handleKeyEvent(KeyEvent.ofChar((char) c), true));
        input.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE), true);
        input.handleKeyEvent(KeyEvent.ofChar('t'), true);
        assertEquals(6, asking.typed());

        input.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER), true);

        assertArrayEquals(
                "secret".getBytes(StandardCharsets.UTF_8), answer.join().get().utf8());
        assertEquals(List.of(new SshPromptMsg.Asked(1, PROMPT), new SshPromptMsg.Closed(1)), sent);
        assertEquals(List.of(true, false), pastingAsKeys);
    }

    @Test
    void escapeCancels() {
        CompletableFuture<Option<SecretBuffer>> answer = asking.ask(1, PROMPT);
        input.handleKeyEvent(KeyEvent.ofChar('x'), true);

        input.handleKeyEvent(KeyEvent.ofKey(KeyCode.ESCAPE), true);

        assertEquals(Option.none(), answer.join());
        assertEquals(new SshPromptMsg.Closed(1), sent.getLast());
        assertEquals(0, asking.typed());
    }

    @Test
    void aWithdrawnQuestionIsAnsweredWithNothing() {
        CompletableFuture<Option<SecretBuffer>> answer = asking.ask(1, PROMPT);

        asking.withdraw(2);
        assertFalse(answer.isDone());
        asking.withdraw(1);

        assertEquals(Option.none(), answer.join());
        assertEquals(List.of(true, false), pastingAsKeys);
    }

    /**
     * The operation can end after the server started asking but before the question reached the render thread: the
     * withdrawal comes first there, and the question then opens nothing.
     */
    @Test
    void aQuestionWithdrawnBeforeItArrivesOpensNoPopup() {
        java.util.Queue<Runnable> renderThread = new java.util.ArrayDeque<>();
        PopupAsking queued = new PopupAsking(renderThread::add, sent::add, pastingAsKeys::add);

        queued.withdraw(1);
        CompletableFuture<Option<SecretBuffer>> answer = queued.ask(1, PROMPT);
        renderThread.forEach(Runnable::run);

        assertTrue(answer.isDone(), "answered");
        assertEquals(Option.none(), answer.join());
        assertEquals(List.of(), sent);
        assertEquals(List.of(), pastingAsKeys);
        assertEquals(0, queued.typed());
    }

    @Test
    void aNewQuestionWithdrawsTheOneThatIsOpen() {
        CompletableFuture<Option<SecretBuffer>> first = asking.ask(1, PROMPT);

        asking.ask(2, PROMPT);

        assertEquals(Option.none(), first.join());
        assertEquals(new SshPromptMsg.Asked(2, PROMPT), sent.getLast());
    }

    @Test
    void aKeyWithCtrlIsLeftToTheAppAndAnyOtherKeyToThePopup() {
        asking.ask(1, PROMPT);

        assertEquals(EventResult.UNHANDLED, input.handleKeyEvent(KeyEvent.ofChar('c', KeyModifiers.CTRL), true));
        assertEquals(EventResult.HANDLED, input.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN), true));
        assertEquals(EventResult.HANDLED, input.handleKeyEvent(KeyEvent.ofChar('?'), true));
        assertEquals(1, asking.typed());
    }

    @Test
    void aPasteThatComesAsAPasteAfterAllIsTakenWithoutItsLineBreaks() {
        CompletableFuture<Option<SecretBuffer>> answer = asking.ask(1, PROMPT);

        input.handlePasteEvent(new PasteEvent("pass\nword\r\n"));
        input.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER), true);

        assertArrayEquals(
                "password".getBytes(StandardCharsets.UTF_8), answer.join().get().utf8());
    }
}
