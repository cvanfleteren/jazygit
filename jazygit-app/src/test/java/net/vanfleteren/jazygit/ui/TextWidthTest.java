package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void linesCountsWrappedTextAsItIsShown() {
        assertEquals(1, TextWidth.lines("short", 56));
        assertEquals(2, TextWidth.lines("a".repeat(40) + " " + "b".repeat(40), 56));
        assertEquals(3, TextWidth.lines("one\ntwo\nthree", 56));
        assertEquals(3, TextWidth.lines("one\n\nthree", 56));
        assertEquals(3, TextWidth.lines("x".repeat(120), 56));
        assertEquals(1, TextWidth.lines("", 56));
        assertEquals(2, TextWidth.lines("trailing\n", 56));
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
