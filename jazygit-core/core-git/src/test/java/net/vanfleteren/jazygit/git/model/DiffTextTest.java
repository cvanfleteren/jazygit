package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DiffTextTest {

    private static final String DIFF = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1 +1 @@
            -count = 1
            +count = 2
            """;

    @Test
    void aDiffOfAsManyFilesAndHunksAsTheMostLinesShownIsParsedOnAThreadOfLittleStack() throws InterruptedException {
        // 50 000 lines: files of one line each, then a file of hunks of one line each, as -U0 reads them.
        String files = IntStream.range(0, 4000)
                .mapToObj(i ->
                        "diff --git a/f%d b/f%d\n--- a/f%d\n+++ b/f%d\n@@ -1 +1 @@\n+%d\n".formatted(i, i, i, i, i))
                .collect(Collectors.joining());
        String hunks = IntStream.range(0, 12_998)
                .mapToObj(i -> "@@ -%d,0 +%d @@\n+%d\n".formatted(2 * i, 2 * i + 1, i))
                .collect(Collectors.joining("", "diff --git a/h b/h\n--- a/h\n+++ b/h\n", ""));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicInteger parsed = new AtomicInteger();
        Thread parsing = new Thread(
                null,
                () -> {
                    try {
                        parsed.set(DiffText.of(files + hunks).parsed().files().size());
                    } catch (Throwable e) {
                        thrown.set(e);
                    }
                },
                "parsing",
                256 * 1024);

        parsing.start();
        parsing.join();

        assertNull(thrown.get());
        assertEquals(4001, parsed.get());
    }

    @Test
    void aDiffReadAgainIsEqualToTheOneReadBefore() {
        DiffText before = DiffText.of(DIFF);
        DiffText again = DiffText.of(new String(DIFF));

        assertNotSame(before.parsed(), again.parsed());
        assertEquals(before, again);
        assertEquals(before.hashCode(), again.hashCode());
    }

    @Test
    void aTruncatedDiffIsNotEqualToTheWholeOfTheSameText() {
        assertNotEquals(DiffText.of(DIFF), DiffText.of(DIFF, true));
    }
}
