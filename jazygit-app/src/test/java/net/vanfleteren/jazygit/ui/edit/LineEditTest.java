package net.vanfleteren.jazygit.ui.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The readline keys, and what they do to the fields of the toolkit.
 */
class LineEditTest {

    private static final KeyModifiers ALT = KeyModifiers.of(false, true, false);

    @Test
    void theKeysAreTheOnesOfReadline() {
        assertEquals(
                Option.of(LineCommand.BEGINNING_OF_LINE), LineEdit.command(KeyEvent.ofChar('a', KeyModifiers.CTRL)));
        assertEquals(Option.of(LineCommand.END_OF_LINE), LineEdit.command(KeyEvent.ofChar('e', KeyModifiers.CTRL)));
        assertEquals(
                Option.of(LineCommand.UNIX_WORD_RUBOUT), LineEdit.command(KeyEvent.ofChar('w', KeyModifiers.CTRL)));
        assertEquals(
                Option.of(LineCommand.UNIX_LINE_DISCARD), LineEdit.command(KeyEvent.ofChar('u', KeyModifiers.CTRL)));
        assertEquals(Option.of(LineCommand.KILL_LINE), LineEdit.command(KeyEvent.ofChar('k', KeyModifiers.CTRL)));
        assertEquals(Option.of(LineCommand.BACKWARD_WORD), LineEdit.command(KeyEvent.ofChar('b', ALT)));
        assertEquals(Option.of(LineCommand.FORWARD_WORD), LineEdit.command(KeyEvent.ofChar('f', ALT)));
        assertEquals(Option.of(LineCommand.KILL_WORD), LineEdit.command(KeyEvent.ofChar('d', ALT)));
        assertEquals(
                Option.of(LineCommand.BACKWARD_KILL_WORD), LineEdit.command(KeyEvent.ofKey(KeyCode.BACKSPACE, ALT)));
    }

    @Test
    void ctrlWithTheArrowsAndDeleteGoesByWords() {
        assertEquals(
                Option.of(LineCommand.BACKWARD_WORD),
                LineEdit.command(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.CTRL)));
        assertEquals(
                Option.of(LineCommand.FORWARD_WORD),
                LineEdit.command(KeyEvent.ofKey(KeyCode.RIGHT, KeyModifiers.CTRL)));
        assertEquals(
                Option.of(LineCommand.KILL_WORD), LineEdit.command(KeyEvent.ofKey(KeyCode.DELETE, KeyModifiers.CTRL)));
    }

    @Test
    void otherKeysAreLeftToTheField() {
        assertEquals(Option.none(), LineEdit.command(KeyEvent.ofChar('w')));
        assertEquals(Option.none(), LineEdit.command(KeyEvent.ofChar('b', KeyModifiers.CTRL)));
        assertEquals(Option.none(), LineEdit.command(KeyEvent.ofChar('w', ALT)));
        assertEquals(Option.none(), LineEdit.command(KeyEvent.ofKey(KeyCode.LEFT)));
        assertEquals(Option.none(), LineEdit.command(KeyEvent.ofKey(KeyCode.BACKSPACE)));
    }

    @Test
    void editsATextInput() {
        TextInputState state = new TextInputState("git checkout feature/foo-bar");
        state.moveCursorToEnd();

        assertTrue(LineEdit.handle(state, KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.CTRL)));
        assertEquals(25, state.cursorPosition());
        assertTrue(LineEdit.handle(state, KeyEvent.ofChar('w', KeyModifiers.CTRL)));
        assertEquals("git checkout bar", state.text());
        assertEquals(13, state.cursorPosition());
        assertFalse(LineEdit.handle(state, KeyEvent.ofChar('x')));
        assertEquals("git checkout bar", state.text());
    }

    @Test
    void editsATextAreaAcrossLines() {
        TextAreaState state = new TextAreaState("first line\nsecond line");

        assertTrue(LineEdit.handle(state, KeyEvent.ofChar('a', KeyModifiers.CTRL)));
        assertEquals(1, state.cursorRow());
        assertEquals(0, state.cursorCol());
        assertTrue(LineEdit.handle(state, KeyEvent.ofChar('w', KeyModifiers.CTRL)));
        assertEquals("first second line", state.text());
        assertEquals(0, state.cursorRow());
        assertEquals(6, state.cursorCol());
        assertTrue(LineEdit.handle(state, KeyEvent.ofChar('k', KeyModifiers.CTRL)));
        assertEquals("first ", state.text());
    }
}
