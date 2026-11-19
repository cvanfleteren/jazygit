package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

/**
 * Which keys count as a plain character key for the global shortcuts.
 */
class GlobalKeysTest {

    @Test
    void thePlainCharacterIsTheKey() {
        assertTrue(GlobalKeys.isQuitKey(KeyEvent.ofChar('q')));
    }

    @Test
    void aModifierOtherThanShiftMakesItAnotherKey() {
        assertFalse(GlobalKeys.isQuitKey(KeyEvent.ofChar('q', KeyModifiers.CTRL)));
        assertFalse(GlobalKeys.isQuitKey(KeyEvent.ofChar('q', KeyModifiers.ALT)));
        assertFalse(GlobalKeys.isQuitKey(KeyEvent.ofChar('q', KeyModifiers.SUPER)));
    }

    @Test
    void shiftIsPartOfTheCharacter() {
        assertTrue(GlobalKeys.isInProgressKey(KeyEvent.ofChar('M', KeyModifiers.SHIFT)));
    }
}
