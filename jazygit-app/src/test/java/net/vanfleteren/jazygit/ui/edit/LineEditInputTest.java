package net.vanfleteren.jazygit.ui.edit;

import static dev.tamboui.toolkit.Toolkit.textInput;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.widgets.input.TextInputState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A text input that also knows the readline keys.
 */
class LineEditInputTest {

    private static final Rect AREA = new Rect(0, 0, 20, 1);
    private static final String ID = "field";

    @Test
    void theCaretIsTheTerminalsCursorOnAnUnstyledCell() {
        TextInputState state = new TextInputState();
        state.setText("abc");
        state.moveCursorToEnd();
        Buffer buffer = Buffer.empty(AREA);
        Frame frame = Frame.forTesting(buffer);
        // The input only shows its caret while it has the focus.
        DefaultRenderContext context = DefaultRenderContext.createEmpty();
        context.focusManager().setFocus(ID);

        new LineEditInput(textInput(state), state, ID).render(frame, AREA, context);

        // After the text: a reversed cell there would cancel out a block cursor.
        assertEquals(Optional.of(new Position(3, 0)), frame.cursorPosition());
        assertFalse(buffer.get(3, 0).style().addModifiers().contains(Modifier.REVERSED));
    }
}
