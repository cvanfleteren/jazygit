package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.text.CharWidth;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.stream.IntStream;

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
     * {@code text}, cut to {@code width} columns when it is wider, ending in an ellipsis within those columns: only the
     * ellipsis when there is no room for more, nothing when there is no room at all. Characters are kept whole.
     */
    public static String cut(String text, int width) {
        if (of(text) <= width) {
            return text;
        }
        if (width <= 0) {
            return "";
        }
        return width <= of(CUT)
                ? CUT
                : CharWidth.truncateWithEllipsis(text, width, CUT, CharWidth.TruncatePosition.END);
    }

    /**
     * The start of {@code text} that fits in {@code width} columns, without an ellipsis. Characters are kept whole,
     * also those of more than one code point, like a flag.
     */
    public static String start(String text, int width) {
        return CharWidth.substringByWidth(text, Math.max(0, width));
    }

    /**
     * {@code text} with spaces before it to take {@code width} columns, as it is when it is that wide or wider.
     */
    public static String alignRight(String text, int width) {
        return " ".repeat(Math.max(0, width - of(text))) + text;
    }

    /**
     * How many lines {@code text} takes when wrapped on words at {@code width} columns, as a word wrapping
     * {@link Paragraph} shows it: each of its own lines starts a new one, and a word wider than the width is cut over
     * several. Measured by drawing it, so it is exactly what is shown.
     */
    public static int lines(String text, int width) {
        return lines(text, width, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #lines(String, int)}, but no more than {@code maxLines}: only that many are drawn to count them, so
     * a long text costs no more than a short one.
     */
    public static int lines(String text, int width, int maxLines) {
        int ownLines = text.split("\\R", -1).length;
        if (width <= 0) {
            return Math.min(maxLines, ownLines);
        }
        // Wider than the text it wraps nowhere.
        int columns = Math.min(width, Math.max(1, of(text)));
        Rect area = new Rect(0, 0, columns, (int) Math.min(mostLines(text, columns, ownLines), maxLines + 1L));
        Buffer buffer = Buffer.empty(area);
        Paragraph.builder().text(text).overflow(Overflow.WRAP_WORD).build().render(area, buffer);
        int drawn = IntStream.range(0, area.height())
                        .filter(y -> IntStream.range(0, columns)
                                .anyMatch(x -> !buffer.get(x, y).symbol().isBlank()))
                        .max()
                        .orElse(-1)
                + 1;
        // Blank lines at the end show nothing, but take their room.
        return Math.min(maxLines, Math.max(drawn + blankAtEnd(text), ownLines));
    }

    /**
     * How many of the own lines of {@code text} at its end are blank.
     */
    static int blankAtEnd(String text) {
        String[] own = text.split("\\R", -1);
        return (int) IntStream.iterate(own.length - 1, i -> i >= 0 && own[i].isBlank(), i -> i - 1)
                .count();
    }

    /**
     * The most lines {@code text} can take wrapped at {@code columns}, to draw no more than that. Two lines one
     * after the other hold more than the width less a column: else the first word of the second would have fitted
     * on the first. So each of its own lines takes at most twice its columns over that, plus one; and one more for
     * rounding down.
     */
    private static int mostLines(String text, int columns, int ownLines) {
        return 2 * of(text) / Math.max(1, columns - 1) + ownLines + 1;
    }
}
