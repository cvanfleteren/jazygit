package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.backend.panama.unix.LibC;
import org.junit.jupiter.api.Test;

/**
 * Whether the system has the LibC the size of a cell is asked of (ADR 191).
 */
class CellSizeTest {

    @Test
    void aClassWhoseStaticFieldsCannotBeMadeIsNoLibC() {
        assertFalse(CellSize.loads(NoLibC.class));
    }

    @Test
    void theLibCOfThisSystemIsMade() {
        assertTrue(CellSize.loads(LibC.class));
    }

    /**
     * As LibC where the system has none: making its static fields fails.
     */
    static final class NoLibC {
        static final int FIELD = missing();

        private static int missing() {
            throw new UnsupportedOperationException("no LibC here");
        }
    }
}
