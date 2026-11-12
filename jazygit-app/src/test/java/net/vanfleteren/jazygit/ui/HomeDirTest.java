package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HomeDirTest {

    private static final String HOME = System.getProperty("user.home");

    @Test
    void theHomeDirectoryIsATilde() {
        assertEquals("~", HomeDir.shown(HOME));
    }

    @Test
    void aPathInTheHomeDirectoryStartsWithATilde() {
        assertEquals("~/repos/app", HomeDir.shown(HOME + "/repos/app"));
    }

    @Test
    void aPathThatOnlyStartsWithTheSameCharactersIsShownInFull() {
        assertEquals(HOME + "-other/app", HomeDir.shown(HOME + "-other/app"));
    }

    @Test
    void aPathOutsideTheHomeDirectoryIsShownInFull() {
        assertEquals("/srv/repo", HomeDir.shown("/srv/repo"));
    }
}
