package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Typing in the filter of the bases.
 */
class BasesFieldTest {

    private static final KeyEvent BACKSPACE = KeyEvent.ofKey(KeyCode.BACKSPACE);

    @Test
    void backspaceTakesTheLastCharacterOff() {
        assertEquals(Option.of("mai"), BasesField.filtered("main", BACKSPACE));
        assertEquals(Option.of(""), BasesField.filtered("", BACKSPACE));
    }

    @Test
    void backspaceTakesACharacterOfMoreThanOneCodePointOffWhole() {
        assertEquals(Option.of("feat/"), BasesField.filtered("feat/🇧🇪", BACKSPACE));
        assertEquals(Option.of("feat/"), BasesField.filtered("feat/👨‍🦲", BACKSPACE));
        assertEquals(Option.of("feat/"), BasesField.filtered("feat/👍🏽", BACKSPACE));
    }
}
