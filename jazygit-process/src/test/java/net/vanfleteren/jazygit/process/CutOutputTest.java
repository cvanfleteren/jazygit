package net.vanfleteren.jazygit.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CutOutputTest {

    @Test
    void aCharacterWrittenInPartsIsReadWhole() {
        byte[] bytes = "é€\n".getBytes(StandardCharsets.UTF_8);
        CutOutput output = new CutOutput(new Limit(10, 10));

        for (byte b : bytes) {
            output.write(b);
        }

        assertEquals(new Limited("é€\n", false), output.limited());
    }

    @Test
    void anOutputThatEndsInTheMiddleOfACharacterHasItReplaced() {
        CutOutput output = new CutOutput(new Limit(10, 10));

        output.write(new byte[] {'a', (byte) 0xE2, (byte) 0x82}, 0, 3);

        assertEquals(new Limited("a�", false), output.limited());
    }

    @Test
    void theProgramIsStoppedOnceItPrintedMoreThanTheLimitAndTheRestIsDropped() {
        AtomicInteger stopped = new AtomicInteger();
        CutOutput output = new CutOutput(new Limit(2, 100));
        output.whenOver(stopped::incrementAndGet);
        byte[] line = "line\n".getBytes(StandardCharsets.UTF_8);

        output.write(line, 0, line.length);
        output.write(line, 0, line.length);
        assertEquals(0, stopped.get());
        output.write(line, 0, line.length);
        output.write(line, 0, line.length);

        assertEquals(1, stopped.get());
        assertEquals(new Limited("line\nline\n", true), output.limited());
    }

    @Test
    void aProgramAlreadyOverTheLimitWhenItIsKnownIsStoppedAtOnce() {
        AtomicInteger stopped = new AtomicInteger();
        CutOutput output = new CutOutput(new Limit(1, 100));
        byte[] lines = "a\nb\n".getBytes(StandardCharsets.UTF_8);
        output.write(lines, 0, lines.length);

        output.whenOver(stopped::incrementAndGet);

        assertEquals(1, stopped.get());
    }

    @Test
    void aLineLongerThanTheLimitLeavesNothing() {
        CutOutput output = new CutOutput(new Limit(10, 1000));
        byte[] chunk = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < 1000; i++) {
            output.write(chunk, 0, chunk.length);
        }

        Limited kept = output.limited();
        assertTrue(kept.cut());
        assertEquals("", kept.text());
    }
}
