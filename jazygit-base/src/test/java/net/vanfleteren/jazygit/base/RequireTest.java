package net.vanfleteren.jazygit.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RequireTest {

    @Test
    void whatHoldsPasses() {
        assertDoesNotThrow(() -> Require.that(true, () -> "never"));
    }

    @Test
    void whatDoesNotHoldThrowsTheProblem() {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> Require.that(false, () -> "two keys"));

        assertEquals("two keys", thrown.getMessage());
    }

    @Test
    void theProblemIsOnlyMadeWhenItFails() {
        assertDoesNotThrow(() -> Require.that(true, () -> {
            throw new AssertionError("made");
        }));
    }
}
