package net.vanfleteren.jazygit.kernel.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
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

    @Test
    void aCharacterTypedGoesAtTheEnd() {
        assertEquals(Option.of("main"), BasesField.filtered("mai", KeyEvent.ofChar('n')));
    }

    @Test
    void aCharacterTypedWithAltGrGoesAtTheEnd() {
        // AltGr arrives as Ctrl+Alt, with the text it makes.
        KeyEvent euro = KeyEvent.builder()
                .modifiers(KeyModifiers.of(true, true, false))
                .character('e')
                .text("€")
                .build();

        assertEquals(Option.of("price-€"), BasesField.filtered("price-", euro));
    }

    @Test
    void aDigitOnTheKeypadGoesAtTheEnd() {
        KeyEvent seven = KeyEvent.builder().code(KeyCode.KP_7).character('7').build();

        assertEquals(Option.of("release-7"), BasesField.filtered("release-", seven));
    }

    @Test
    void aShortcutIsNotTyped() {
        assertEquals(Option.none(), BasesField.filtered("main", KeyEvent.ofChar('x', KeyModifiers.ALT)));
        assertEquals(Option.none(), BasesField.filtered("main", KeyEvent.ofChar('x', KeyModifiers.CTRL)));
    }

    @Test
    void deleteIsNotTyped() {
        assertEquals(Option.none(), BasesField.filtered("main", KeyEvent.ofChar(127)));
    }
}
