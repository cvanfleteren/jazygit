package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * How much room text takes on the screen: in columns, not in Java characters. An emoji or a CJK character takes two
 * columns, and an emoji is two Java characters, which cutting by {@link String#length()} could split.
 */
public final class TextWidth {

    private static final String CUT = "…";

    private TextWidth() {}

    /**
     * The columns {@code text} takes on one line.
     */
    public static int of(String text) {
        return CharWidth.of(text);
    }

    /**
     * {@code text}, cut to {@code width} columns when it is wider, ending in an ellipsis within those columns.
     * Characters are kept whole.
     */
    public static String cut(String text, int width) {
        return of(text) <= width
                ? text
                : CharWidth.truncateWithEllipsis(text, width, CUT, CharWidth.TruncatePosition.END);
    }

    /**
     * How many lines {@code text} takes when wrapped on words at {@code width} columns, as a word wrapping
     * {@link Paragraph} shows it: each of its own lines starts a new one, and a word wider than the width is cut over
     * several. Measured by drawing it, so it is exactly what is shown.
     */
    public static int lines(String text, int width) {
        int ownLines = text.split("\\R", -1).length;
        if (width <= 0) {
            return ownLines;
        }
        // Wider than the text it wraps nowhere; a line wraps at most once per column it takes, and its own lines
        // start new ones.
        int columns = Math.min(width, Math.max(1, of(text)));
        Rect area = new Rect(0, 0, columns, Math.max(1, of(text) + ownLines));
        Buffer buffer = Buffer.empty(area);
        Paragraph.builder().text(text).overflow(Overflow.WRAP_WORD).build().render(area, buffer);
        int drawn = 0;
        for (int y = 0; y < area.height(); y++) {
            for (int x = 0; x < columns; x++) {
                if (!buffer.get(x, y).symbol().isBlank()) {
                    drawn = y + 1;
                    break;
                }
            }
        }
        // Blank lines at the end show nothing, but take their room.
        return Math.max(drawn, ownLines);
    }
}
