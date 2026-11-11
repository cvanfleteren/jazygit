package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void anUppercaseFilterMatchesTheFirstLettersOfWordsFollowingEachOther() {
        assertTrue(ListFilter.matches("CCM", "src/main/java/CapitalCaseMatching.java"));
        assertTrue(ListFilter.matches("CCM", "notes/CanCanMamaHere.txt"));
        assertTrue(ListFilter.matches("CCM", "can-can-mama.txt"));
        assertTrue(ListFilter.matches("CCM", "can_can_mama.txt"));
        assertFalse(ListFilter.matches("CCM", "src/CanCanDaddy.java"));
        assertFalse(ListFilter.matches("CCM", "CapitalFooCaseMatching.java"));
    }

    @Test
    void theWordsMayRunOverASlashOrADot() {
        assertTrue(ListFilter.matches("SMJ", "src/main/java/Foo.java"));
        assertTrue(ListFilter.matches("FJ", "src/main/java/Foo.java"));
    }

    @Test
    void aLowercaseFilterDoesNotMatchFirstLetters() {
        assertFalse(ListFilter.matches("ccm", "CapitalCaseMatching.java"));
    }

    @Test
    void wordsStartAtSeparatorsAtCaseChangesAndAtTheEndOfAnAcronym() {
        assertEquals("cs", ListFilter.initials("CapitalSomething"));
        assertEquals("hs", ListFilter.initials("HTTPServer"));
        assertEquals("ade", ListFilter.initials("ABC-d e"));
        assertEquals("vd", ListFilter.initials("v2Dialog"));
        assertEquals("fbbj", ListFilter.initials("foo_bar-Baz.java"));
        assertEquals("", ListFilter.initials("--/"));
    }
}
