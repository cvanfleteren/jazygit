package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.backend.panama.unix.LibC;
import org.junit.jupiter.api.Test;

/**
 * Whether the system has the LibC the size of a cell is asked of (ADR 188).
 */
class CellSizeTest {

    @Test
    void aClassThatCannotBeLoadedIsNoLibC() {
        assertFalse(CellSize.loads("net.vanfleteren.jazygit.app.NoSuchLibC"));
    }

    @Test
    void theLibCOfThisSystemIsLoadedAndMade() {
        assertTrue(CellSize.loads(LibC.class.getName()));
    }
}
