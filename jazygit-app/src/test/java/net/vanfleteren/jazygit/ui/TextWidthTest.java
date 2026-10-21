package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.time.Duration;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Text measured in columns: an emoji or a CJK character takes two, and is never split.
 */
class TextWidthTest {

    @Test
    void wideCharactersTakeTwoColumns() {
        assertEquals(5, TextWidth.of("hello"));
        assertEquals(4, TextWidth.of("👍👍"));
        assertEquals(6, TextWidth.of("日本語"));
        assertEquals(3, TextWidth.of("été"));
    }

    @Test
    void cuttingKeepsWithinTheColumnsAndNeverSplitsACharacter() {
        assertEquals("hello", TextWidth.cut("hello", 5));
        assertEquals("hell…", TextWidth.cut("hello world", 5));
        // Two emoji and the ellipsis take five columns; a third would not fit.
        assertEquals("ab👍…", TextWidth.cut("ab👍👍👍", 5));
        assertEquals("日本…", TextWidth.cut("日本語です", 6));
        assertEquals("👍👍", TextWidth.cut("👍👍", 4));
    }

    @Test
    void withRoomForTheEllipsisOnlyItIsAllThatShows() {
        assertEquals("…", TextWidth.cut("hello", 1));
        assertEquals("", TextWidth.cut("hello", 0));
        assertEquals("h", TextWidth.cut("h", 1));
        // A wide character does not fit in one column.
        assertEquals("…", TextWidth.cut("日本", 1));
    }

    @Test
    void theStartKeepsWithinTheColumnsWithoutAnEllipsis() {
        assertEquals("he", TextWidth.start("hello", 2));
        assertEquals("日", TextWidth.start("日本", 3));
        assertEquals("", TextWidth.start("日本", 1));
        assertEquals("🇧🇪", TextWidth.start("🇧🇪🇧🇪", 3));
        assertEquals("", TextWidth.start("hello", -1));
    }

    @Test
    void aligningRightPadsToTheColumns() {
        assertEquals("   ab", TextWidth.alignRight("ab", 5));
        assertEquals(" 日本", TextWidth.alignRight("日本", 5));
        assertEquals("hello", TextWidth.alignRight("hello", 3));
    }

    @Test
    void linesStopsAtTheMostAsked() {
        assertEquals(2, TextWidth.lines("one\ntwo\nthree", 56, 2));
        assertEquals(3, TextWidth.lines("x".repeat(120), 56, 5));
        assertEquals(0, TextWidth.lines("anything", 56, 0));
    }

    @Test
    void linesOfALongTextCostLittle() {
        // As a long error from gh or git: one line of 200k characters, and 500 lines of a usual one.
        String oneLine = "word ".repeat(40_000);
        String manyLines = "fatal: could not read from remote repository, check access rights\n".repeat(500);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals(3, TextWidth.lines(oneLine, 56, 3));
            assertEquals(3, TextWidth.lines(manyLines, 56, 3));
            // Eleven words of five columns to a line of 56.
            assertEquals(3_637, TextWidth.lines(oneLine, 56));
            // Each of the 66 columns long lines takes two, and the last is blank.
            assertEquals(1_001, TextWidth.lines(manyLines, 56));
        });
    }

    @Test
    void drawingOnlyTheMostLinesItCanTakeCountsAllOfThem() {
        // Words of every width, wide ones too, and lines of their own, some blank: counted against drawing in a
        // buffer as high as can be needed.
        Random random = new Random(42);
        java.util.List<String> words =
                java.util.List.of("a", "to", "the", "words", "wrapping", "日本語", "👍", "x".repeat(23), "", "\n");
        for (int text = 0; text < 200; text++) {
            String written = IntStream.range(0, random.nextInt(60))
                    .mapToObj(i -> words.get(random.nextInt(words.size())))
                    .collect(Collectors.joining(" "));
            for (int width : new int[] {1, 2, 3, 5, 8, 13, 21, 56}) {
                assertEquals(drawnInFull(written, width), TextWidth.lines(written, width), width + ": " + written);
            }
        }
    }

    /**
     * The lines {@code text} takes, drawn in a buffer as high as its columns and its own lines.
     */
    private static int drawnInFull(String text, int width) {
        int ownLines = text.split("\\R", -1).length;
        Rect area = new Rect(0, 0, width, TextWidth.of(text) + ownLines + 1);
        Buffer buffer = Buffer.empty(area);
        Paragraph.builder().text(text).overflow(Overflow.WRAP_WORD).build().render(area, buffer);
        int drawn = 0;
        for (int y = 0; y < area.height(); y++) {
            for (int x = 0; x < width; x++) {
                if (!buffer.get(x, y).symbol().isBlank()) {
                    drawn = y + 1;
                }
            }
        }
        return Math.max(drawn + TextWidth.blankAtEnd(text), ownLines);
    }

    @Test
    void linesCountsWrappedTextAsItIsShown() {
        assertEquals(1, TextWidth.lines("short", 56));
        assertEquals(2, TextWidth.lines("a".repeat(40) + " " + "b".repeat(40), 56));
        assertEquals(3, TextWidth.lines("one\ntwo\nthree", 56));
        assertEquals(3, TextWidth.lines("one\n\nthree", 56));
        assertEquals(3, TextWidth.lines("x".repeat(120), 56));
        assertEquals(1, TextWidth.lines("", 56));
        assertEquals(2, TextWidth.lines("trailing\n", 56));
        // Blank lines at the end take their room also after wrapped ones.
        assertEquals(4, TextWidth.lines("x".repeat(100) + "\n\n", 56));
        assertEquals(2, TextWidth.lines("\n", 56));
        // As wide as can be, it wraps nowhere.
        assertEquals(2, TextWidth.lines("one\ntwo", Integer.MAX_VALUE));
    }

    @Test
    void wideCharactersWrapSooner() {
        // Ten CJK characters take twenty columns: two lines of ten columns.
        assertEquals(2, TextWidth.lines("日本語日本語日本語日", 10));
        // Six emoji words of four columns: two to a line of ten columns.
        assertEquals(3, TextWidth.lines("👍👍 👍👍 👍👍 👍👍 👍👍 👍👍", 10));
    }
}
