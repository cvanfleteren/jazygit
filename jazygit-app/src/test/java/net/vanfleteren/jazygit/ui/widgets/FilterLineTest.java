package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.RenderContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The line above a list to type what it shows in.
 */
class FilterLineTest {

    private static final Rect AREA = new Rect(0, 0, 20, 1);

    @Test
    void theCaretIsTheTerminalsCursorOnAnUnstyledCell() {
        Buffer buffer = Buffer.empty(AREA);
        Frame frame = Frame.forTesting(buffer);

        new FilterLine().field("abc").render(frame, AREA, RenderContext.empty());

        // After the "/ " and the text: a reversed cell there would cancel out a block cursor.
        assertEquals(Optional.of(new Position(5, 0)), frame.cursorPosition());
        assertFalse(buffer.get(5, 0).style().addModifiers().contains(Modifier.REVERSED));
    }
}
