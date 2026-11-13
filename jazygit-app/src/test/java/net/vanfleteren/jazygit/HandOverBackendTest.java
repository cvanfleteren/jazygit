package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.terminal.KittyKeyboardSupport;
import dev.tamboui.terminal.TestBackend;
import dev.tamboui.tui.TuiConfig;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The reports of the focus are taken out of what the terminal sends, before TamboUI reads it as keys (ADR 58).
 */
class HandOverBackendTest {

    private static final int NO_KEY = -2;

    @Test
    void theReportsOfTheFocusAreToldAndNotReadAsKeys() throws IOException {
        List<Boolean> focus = new ArrayList<>();
        HandOverBackend backend = new HandOverBackend(typed("a\u001b[Ob\u001b[I"), focus::add);

        assertEquals(List.of((int) 'a', (int) 'b', NO_KEY), keys(backend, 3));
        assertEquals(List.of(false, true), focus);
    }

    @Test
    void aKeyThatCameAfterAFocusReportIsReadEvenWhenTheReportTookTheWholeWait() throws IOException {
        List<Boolean> focus = new ArrayList<>();
        Deque<Integer> sent = new ArrayDeque<>("a\u001b[Ob".chars().boxed().toList());
        HandOverBackend backend = new HandOverBackend(
                new TestBackend(80, 24) {
                    @Override
                    public int read(int timeoutMs) throws IOException {
                        if (sent.peek() != null && sent.peek() == 'O') {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                throw new InterruptedIOException();
                            }
                        }
                        return sent.isEmpty() ? NO_KEY : sent.poll();
                    }

                    @Override
                    public int peek(int timeoutMs) {
                        return sent.isEmpty() ? NO_KEY : sent.peek();
                    }
                },
                focus::add);

        assertEquals(List.of((int) 'a', (int) 'b', NO_KEY), keys(backend, 3));
        assertEquals(List.of(false, true), focus);
    }

    @Test
    void aKeyAfterTheFocusWasLostTellsItIsBackWhenItsReportGotLost() throws IOException {
        List<Boolean> focus = new ArrayList<>();
        HandOverBackend backend = new HandOverBackend(typed("\u001b[Oab"), focus::add);

        assertEquals(List.of((int) 'a', (int) 'b'), keys(backend, 2));
        assertEquals(List.of(false, true), focus);
    }

    @Test
    void otherEscapeSequencesAreReadAsTheyCame() throws IOException {
        List<Boolean> focus = new ArrayList<>();
        HandOverBackend backend = new HandOverBackend(typed("\u001b[A\u001b"), focus::add);

        assertEquals((int) '\u001b', backend.read(10));
        assertEquals((int) '[', backend.peek(10));
        assertEquals(List.of((int) '[', (int) 'A', 27, NO_KEY), keys(backend, 4));
        assertTrue(focus.isEmpty());
    }

    @Test
    void aKeyLetGoIsNotReadButAHeldOneIs() throws IOException {
        // Left held: a press, a repeat and its release; then a released a, as CSI u, and a click, as SGR.
        HandOverBackend backend = new HandOverBackend(
                typed("\u001b[D\u001b[1;1:2D\u001b[1;1:3D\u001b[97;1:3ux\u001b[<0;3;4M"), focused -> {});

        assertEquals("\u001b[D\u001b[1;1:2Dx\u001b[<0;3;4M", read(backend));
    }

    @Test
    void sequencesThatAreNoKeyLetGoAreReadAsTheyCame() throws IOException {
        // A click as X10 sends it, its bytes after the M; F1 of the Linux console; a key let go cut off before its
        // final byte, which cannot be told from anything else.
        String sent = "\u001b[M !\"\u001b[[A\u001b[1;1:3";
        HandOverBackend backend = new HandOverBackend(typed(sent), focused -> {});

        assertEquals(sent, read(backend));
    }

    @Test
    void aKeyLetGoAfterTheFocusWasLostDoesNotTellItIsBack() throws IOException {
        List<Boolean> focus = new ArrayList<>();
        HandOverBackend backend = new HandOverBackend(typed("\u001b[O\u001b[57443;3:3u"), focus::add);

        assertEquals(NO_KEY, backend.read(10));
        assertEquals(List.of(false), focus);
    }

    @Test
    void theTerminalIsAskedToTellAHeldKeyFromAPressedOne() throws IOException {
        TestBackend terminal = typed("");
        HandOverBackend backend = new HandOverBackend(terminal, focused -> {});

        backend.enableKeyboardProtocol(TuiConfig.defaults().keyboardProtocol());

        assertTrue(
                terminal.rawOutput()
                        .contains(KittyKeyboardSupport.pushSequence(
                                KittyKeyboardSupport.AUTO_FLAGS | KittyKeyboardSupport.REPORT_EVENT_TYPES)),
                terminal.rawOutput());
    }

    @Test
    void theKeyboardEnhancementsAreOffWhileTheTerminalIsHandedOver() throws IOException {
        TestBackend terminal = typed("");
        HandOverBackend backend = new HandOverBackend(terminal, focused -> {});
        TuiConfig config = TuiConfig.defaults();

        backend.leave(config);
        String left = terminal.rawOutput();
        backend.comeBack(config);
        String back = terminal.rawOutput().substring(left.length());

        assertTrue(left.contains(KittyKeyboardSupport.popSequence()), left);
        assertTrue(
                back.contains(KittyKeyboardSupport.pushSequence(
                        KittyKeyboardSupport.AUTO_FLAGS | KittyKeyboardSupport.REPORT_EVENT_TYPES)),
                back);
    }

    @Test
    void theTerminalReportsTheFocusOnlyOnTheAlternateScreen() throws IOException {
        TestBackend terminal = typed("");
        HandOverBackend backend = new HandOverBackend(terminal, focused -> {});

        backend.enterAlternateScreen();
        backend.leaveAlternateScreen();

        assertTrue(terminal.rawOutput().contains("\u001b[?1004h"), terminal.rawOutput());
        assertTrue(
                terminal.rawOutput().indexOf("\u001b[?1004l")
                        > terminal.rawOutput().indexOf("\u001b[?1004h"),
                terminal.rawOutput());
    }

    @Test
    void aReadInWhichNoKeyComesWaitsForItOnce() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        HandOverBackend backend = new HandOverBackend(
                new TestBackend(80, 24) {
                    @Override
                    public int read(int timeoutMs) throws IOException {
                        reads.incrementAndGet();
                        try {
                            Thread.sleep(timeoutMs);
                        } catch (InterruptedException e) {
                            throw new InterruptedIOException();
                        }
                        return NO_KEY;
                    }
                },
                focused -> {});

        assertEquals(NO_KEY, backend.read(40));
        assertEquals(1, reads.get());
    }

    private static List<Integer> keys(HandOverBackend backend, int count) throws IOException {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(backend.read(10));
        }
        return keys;
    }

    /**
     * What {@code backend} reads until no key comes.
     */
    private static String read(HandOverBackend backend) throws IOException {
        StringBuilder read = new StringBuilder();
        for (int key = backend.read(10); key != NO_KEY; key = backend.read(10)) {
            read.appendCodePoint(key);
        }
        return read.toString();
    }

    /**
     * A terminal that sends {@code input}, and then nothing.
     */
    private static TestBackend typed(String input) {
        Deque<Integer> sent = new ArrayDeque<>(input.chars().boxed().toList());
        return new TestBackend(80, 24) {
            @Override
            public int read(int timeoutMs) {
                return sent.isEmpty() ? NO_KEY : sent.poll();
            }

            @Override
            public int peek(int timeoutMs) {
                return sent.isEmpty() ? NO_KEY : sent.peek();
            }
        };
    }
}
