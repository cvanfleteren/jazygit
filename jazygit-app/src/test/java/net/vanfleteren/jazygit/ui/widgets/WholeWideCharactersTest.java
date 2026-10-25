package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import org.junit.jupiter.api.Test;

/**
 * A popup over an emoji keeps no half of it: the terminal would draw a half left standing whole, over the border
 * of the popup, and the rest of the row a column off.
 */
class WholeWideCharactersTest {

    private static final Rect AREA = new Rect(0, 0, 40, 4);
    // Centred, 20 wide on 40: the border of the popup in columns 10 and 29.
    private static final Element POPUP = DialogFrame.of("T", 20, text("Hello"));

    private static Buffer render(Element element) {
        Buffer buffer = Buffer.empty(AREA);
        element.render(Frame.forTesting(buffer), AREA, RenderContext.empty());
        return buffer;
    }

    private static String row(Buffer buffer, int y) {
        StringBuilder row = new StringBuilder();
        for (int x = AREA.left(); x < AREA.right(); x++) {
            row.append(
                    buffer.get(x, y).isContinuation() ? "_" : buffer.get(x, y).symbol());
        }
        return row.toString();
    }

    @Test
    void anEmojiWhoseSecondHalfIsUnderThePopupGoes() {
        // An emoji in columns 9 and 10 on the first row of the popup.
        Element behind = column(text(" "), text("x".repeat(9) + "✅" + "x".repeat(29)));

        Buffer buffer = render(new WholeWideCharacters(stack(behind, POPUP)));

        assertEquals("xxxxxxxxx │Hello             │xxxxxxxxxx", row(buffer, 1));
    }

    @Test
    void anEmojiWhoseFirstHalfIsUnderThePopupGoes() {
        // An emoji in columns 29 and 30 on the first row of the popup.
        Element behind = column(text(" "), text("x".repeat(29) + "✅" + "x".repeat(9)));

        Buffer buffer = render(new WholeWideCharacters(stack(behind, POPUP)));

        assertEquals("xxxxxxxxxx│Hello             │ xxxxxxxxx", row(buffer, 1));
    }

    @Test
    void wholeEmojiStay() {
        Element behind = column(text(" "), text("✅".repeat(5) + "x".repeat(30)));

        Buffer buffer = render(new WholeWideCharacters(stack(behind, POPUP)));

        assertEquals("✅_✅_✅_✅_✅_│Hello             │xxxxxxxxxx", row(buffer, 1));
    }
}
