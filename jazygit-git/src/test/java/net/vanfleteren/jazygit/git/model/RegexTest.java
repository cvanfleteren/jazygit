package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RegexTest {

    @Test
    void twoAreEqualWhenWrittenAlikeThoughCompiledApart() {
        assertEquals(Regex.of("^import "), Regex.of("^import "));
        assertEquals(Regex.of("^import ").hashCode(), Regex.of("^import ").hashCode());
        assertNotEquals(Regex.of("^import "), Regex.of("^package "));
    }

    @Test
    void itFindsAnywhereOrMatchesAll() {
        Regex regex = Regex.of("b+");

        assertTrue(regex.find("abba"));
        assertFalse(regex.matches("abba"));
        assertTrue(regex.matches("bb"));
    }

    @Test
    void globsWrittenAlikeAreEqual() {
        assertEquals(PathGlob.of("**/__snapshots__/*.snap"), PathGlob.of("**/__snapshots__/*.snap"));
    }
}
