package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.Array;
import org.junit.jupiter.api.Test;

class ListFilterTest {

    @Test
    void aFilterMatchesTextThatContainsItIgnoringCase() {
        assertTrue(ListFilter.matches("feat", "origin/Feature-x"));
        assertTrue(ListFilter.matches("", "anything"));
        assertFalse(ListFilter.matches("fix", "origin/feature-x"));
    }

    @Test
    void anUppercaseLetterMakesItMindCase() {
        assertTrue(ListFilter.matches("Feat", "origin/Feature-x"));
        assertFalse(ListFilter.matches("Feat", "origin/feature-x"));
    }

    @Test
    void uppercaseLettersMatchTheFirstLettersOfWordsFollowingEachOther() {
        assertTrue(ListFilter.matches("CCM", "src/main/java/CapitalCaseMatching.java"));
        assertTrue(ListFilter.matches("CCM", "notes/CanCanMamaHere.txt"));
        assertTrue(ListFilter.matches("CCM", "can-can-mama.txt"));
        assertTrue(ListFilter.matches("CCM", "can_can_mama.txt"));
        assertFalse(ListFilter.matches("CCM", "src/CanCanDaddy.java"));
        assertFalse(ListFilter.matches("CCM", "CapitalFooCaseMatching.java"));
    }

    @Test
    void lowercaseLettersAfterOneMatchMoreOfItsWord() {
        assertTrue(ListFilter.matches("CCMat", "CapitalCaseMatching.java"));
        assertTrue(ListFilter.matches("CaCaMa", "can-can-mama.txt"));
        assertTrue(ListFilter.matches("CapCM", "CapitalCaseMatching.java"));
        assertFalse(ListFilter.matches("CCMat", "CapitalCaseMove.java"));
        assertFalse(ListFilter.matches("CCMAt", "CapitalCaseMatching.java"));
    }

    @Test
    void theWordsMayRunOverADotButNotASlash() {
        assertTrue(ListFilter.matches("FJ", "src/main/java/Foo.java"));
        assertTrue(ListFilter.matches("CM", "feature/commit/CopyMsg.java"));
        assertTrue(ListFilter.matches("GC", "feature/github-checks/Foo.java"));
        assertFalse(ListFilter.matches("CM", "feature/commit/MessagePopup.java"));
        assertFalse(ListFilter.matches("SMJ", "src/main/java/Foo.java"));
    }

    @Test
    void oneWordTypedMatchesOnlyAsASubstring() {
        assertFalse(ListFilter.matches("Feat", "frontend/e2e/api/tests/x.txt"));
        assertFalse(ListFilter.matches("M", "src/main/Foo.java"));
    }

    @Test
    void aLowercaseFilterMatchesOnlyAsASubstring() {
        assertFalse(ListFilter.matches("ccm", "CapitalCaseMatching.java"));
    }

    @Test
    void aFilterWithASeparatorMatchesOnlyAsASubstring() {
        assertFalse(ListFilter.matches("C-M", "c-m.txt"));
        assertTrue(ListFilter.matches("C-M", "C-M.txt"));
    }

    @Test
    void anUppercaseLetterStartsAWordTyped() {
        assertEquals(Array.of("C", "C", "Mat"), ListFilter.wordsTyped("CCMat"));
        assertEquals(Array.of("foo", "Bar"), ListFilter.wordsTyped("fooBar"));
    }

    @Test
    void wordsStartAtSeparatorsAtCaseChangesAndAtTheEndOfAnAcronym() {
        assertEquals(Array.of(0, 7), ListFilter.wordStarts("CapitalSomething"));
        assertEquals(Array.of(0, 4), ListFilter.wordStarts("HTTPServer"));
        assertEquals(Array.of(0, 4, 6), ListFilter.wordStarts("ABC-d e"));
        assertEquals(Array.of(0, 2), ListFilter.wordStarts("v2Dialog"));
        assertEquals(Array.of(0, 4, 8, 12), ListFilter.wordStarts("foo_bar-Baz.java"));
        assertEquals(Array.empty(), ListFilter.wordStarts("--/"));
    }
}
