package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.terminal.TestBackend;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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
    void otherEscapeSequencesAreReadAsTheyCame() throws IOException {
        List<Boolean> focus = new ArrayList<>();
        HandOverBackend backend = new HandOverBackend(typed("\u001b[A\u001b"), focus::add);

        assertEquals((int) '\u001b', backend.read(10));
        assertEquals((int) '[', backend.peek(10));
        assertEquals(List.of((int) '[', (int) 'A', 27, NO_KEY), keys(backend, 4));
        assertTrue(focus.isEmpty());
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

    private static List<Integer> keys(HandOverBackend backend, int count) throws IOException {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(backend.read(10));
        }
        return keys;
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
