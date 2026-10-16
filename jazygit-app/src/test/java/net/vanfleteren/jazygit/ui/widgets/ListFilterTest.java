package net.vanfleteren.jazygit.ui.widgets;

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
}
