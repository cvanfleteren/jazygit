package net.vanfleteren.jazygit.process;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LimitTest {

    @Test
    void textWithinTheLimitIsWhole() {
        Limit limit = new Limit(2, 10);

        assertEquals(new Limited("", false), limit.cut(""));
        assertEquals(new Limited("ab\ncd\n", false), limit.cut("ab\ncd\n"));
        assertEquals(new Limited("ab\ncd", false), limit.cut("ab\ncd"));
    }

    @Test
    void moreLinesAreCutOffAfterTheLastThatFits() {
        Limit limit = new Limit(2, 100);

        assertEquals(new Limited("a\nb\n", true), limit.cut("a\nb\nc\n"));
        assertEquals(new Limited("a\nb\n", true), limit.cut("a\nb\nc"));
    }

    @Test
    void moreCharactersAreCutOffAtTheLastWholeLineThatFits() {
        Limit limit = new Limit(100, 7);

        assertEquals(new Limited("abc\n", true), limit.cut("abc\ndef\n"));
        assertEquals(new Limited("abc\ndef", false), limit.cut("abc\ndef"));
        assertEquals(new Limited("abc\n", true), limit.cut("abc\ndefg"));
    }

    @Test
    void aFirstLineLongerThanTheLimitLeavesNothing() {
        assertEquals(new Limited("", true), new Limit(100, 3).cut("abcdef\nx\n"));
    }

    @Test
    void aCarriageReturnIsPartOfItsLine() {
        Limit limit = new Limit(1, 100);

        assertEquals(new Limited("a\rb\r\n", true), limit.cut("a\rb\r\nc\r\n"));
    }
}
