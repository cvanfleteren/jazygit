package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import io.vavr.control.Option;
import java.util.Optional;
import net.vanfleteren.jazygit.ui.Theme.SelectedText;
import org.junit.jupiter.api.Test;

/**
 * The plain text on highlighted rows, in a colour of its own.
 */
class SelectedTextColouredTest {

    private static final Rect AREA = new Rect(0, 0, 20, 2);
    private static final Color ROW = Color.rgb(0x3a, 0x3a, 0x3a);
    private static final Color BRIGHT = Color.rgb(0xe8, 0xe8, 0xe8);
    private static final SelectedText TERMINAL_DEFAULT =
            new SelectedText(Option.of(ROW), Option.none(), Option.of(BRIGHT));

    @Test
    void plainTextOnAHighlightedRowTakesTheColour() {
        Buffer buffer = render(new SelectedTextColoured(text("plain").bg(ROW), TERMINAL_DEFAULT));

        assertEquals(Optional.of(BRIGHT), buffer.get(0, 0).style().fg());
    }

    @Test
    void colouredTextOnAHighlightedRowKeepsItsColour() {
        Buffer buffer = render(new SelectedTextColoured(text("red").red().bg(ROW), TERMINAL_DEFAULT));

        assertEquals(Optional.of(Color.RED), buffer.get(0, 0).style().fg());
    }

    @Test
    void plainTextElsewhereKeepsItsColour() {
        Buffer buffer = render(new SelectedTextColoured(text("plain"), TERMINAL_DEFAULT));

        assertEquals(Optional.empty(), buffer.get(0, 0).style().fg());
    }

    @Test
    void textInTheThemesPlainColourCountsAsPlain() {
        Color themeFg = Color.rgb(0xb8, 0xb8, 0xb8);
        SelectedText selectedText = new SelectedText(Option.of(ROW), Option.of(themeFg), Option.of(BRIGHT));

        Buffer buffer = render(new SelectedTextColoured(
                row(
                        text("plain").fg(themeFg).bg(ROW).length(5),
                        text("red").red().bg(ROW)),
                selectedText));

        assertEquals(Optional.of(BRIGHT), buffer.get(0, 0).style().fg());
        assertEquals(Optional.of(Color.RED), buffer.get(5, 0).style().fg());
    }

    @Test
    void withoutAColourNothingChanges() {
        Buffer buffer = render(new SelectedTextColoured(text("plain").bg(ROW), SelectedText.UNCHANGED));

        assertEquals(Optional.empty(), buffer.get(0, 0).style().fg());
    }

    private static Buffer render(Element element) {
        Buffer buffer = Buffer.empty(AREA);
        element.render(Frame.forTesting(buffer), AREA, RenderContext.empty());
        return buffer;
    }
}
