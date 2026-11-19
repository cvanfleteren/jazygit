package net.vanfleteren.jazygit.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.AbstractList;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import net.vanfleteren.jazygit.ui.content.Drawn;
import net.vanfleteren.jazygit.ui.content.Halves;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.text.TextWidth;

/**
 * The lines of an area as it shows them: those that show, scrolled sideways, and a blank line in place of each of the
 * others, so TamboUI measures only the lines that show (ADR 80, 139, 147). Only functions.
 */
public final class TextWindow {

    /**
     * What an area is given in place of a line that does not show ({@link #windowed}).
     */
    private static final Line BLANK = Line.from(Span.raw(""));

    private TextWindow() {}

    /**
     * {@code line} scrolled {@code column} columns sideways: without its first columns, its spans kept in their
     * styles, as {@link TextWidth#rest} cuts their text.
     */
    public static Line sideways(Line line, int column) {
        return Line.from(List.ofAll(line.spans())
                .foldLeft(
                        Tuple.of(column, List.<Span>empty()),
                        (done, span) -> done._1() == 0
                                ? Tuple.of(0, done._2().prepend(span))
                                : Tuple.of(
                                        Math.max(0, done._1() - span.width()),
                                        done._1() >= span.width()
                                                ? done._2()
                                                : done._2()
                                                        .prepend(new Span(
                                                                TextWidth.rest(span.content(), done._1()),
                                                                span.style()))))
                ._2()
                .reverse()
                .toJavaList());
    }

    /**
     * {@code lines} as an area shows them from {@code row}, in an area {@code height} lines high: the lines that show,
     * scrolled {@code column} columns sideways, and a blank one in place of each of the others. TamboUI's text area
     * measures how wide every line it is given is, each frame, so a long diff would hold up every frame; it still gets
     * as many lines, for its scrollbar and how far it scrolls. The height is that of the frame before, so the lines are
     * kept that would show in an area up to twice as high, which the element scrolls up from its last line at its
     * bottom; none before the area was ever drawn, as that frame is only drawn to learn its size and then drawn again
     * ({@code Fitted}), and drawing every line of a long diff for it took as long as styling it. Those of them that are
     * {@code filling} are
     * {@link #filled} to the edge of an area {@code width} columns wide (ADR 80). Only the lines that show are drawn
     * ({@link Rows}).
     */
    public static Text windowed(Rows lines, int column, int row, int height, int width, IntPredicate filling) {
        return windowed(lines, column, row, height, width, filling, ignored -> Option.none(), ignored -> 0);
    }

    /**
     * {@code lines} as {@link #windowed(Rows, int, int, int, int, IntPredicate)} shows them, but those of the split
     * view: each half of a row its {@code halves} say it has cut to its width from {@code column}, its gutter staying;
     * a row as wide as the area, all of whose columns are {@code kept}, not scrolled. Any other row, as a line of a file
     * the split view draws unified, scrolls as in the unified view (ADR 139), but for as many columns at its start as
     * {@code kept} says: the gutter of a line of a version shown blamed, which stays where it is (ADR 147).
     */
    public static Text windowed(
            Rows lines,
            int column,
            int row,
            int height,
            int width,
            IntPredicate filling,
            IntFunction<Option<Halves>> halves,
            IntUnaryOperator kept) {
        Window window = window(lines.size(), row, height);
        int top = window.top();
        int bottom = window.bottom();
        // Text copies the lines it is given, so each line that shows is cut once.
        return Text.from(new AbstractList<Line>() {
            @Override
            public Line get(int index) {
                if (index < top || index >= bottom) {
                    return BLANK;
                }
                Line drawn = lines.get(index);
                Option<Halves> cut = halves.apply(index);
                if (cut.isDefined()) {
                    return cut.get().shown(drawn, column);
                }
                int keep = kept.applyAsInt(index);
                if (keep == Drawn.PINNED) {
                    return filling.test(index) ? filled(drawn, width) : drawn;
                }
                Line line = filling.test(index) ? filled(drawn, column + width) : drawn;
                if (column == 0) {
                    return line;
                }
                if (keep == 0) {
                    return sideways(line, column);
                }
                List<Span> spans = List.ofAll(line.spans());
                return Line.from(Halves.slice(spans, 0, keep)
                        .appendAll(Halves.slice(spans, keep + column, Integer.MAX_VALUE))
                        .toJavaList());
            }

            @Override
            public int size() {
                return lines.size();
            }
        });
    }

    /**
     * The lines of {@code size} that {@link #windowed} shows from {@code row} in an area {@code height} lines high: from
     * the first up to, not with, the second; none at {@code row} while the area was never drawn, its height 0.
     */
    public static Window window(int size, int row, int height) {
        int high = Math.max(0, 2 * height);
        int top = Math.clamp(row, 0, Math.max(0, size - high));
        return new Window(top, Math.max(top, Math.min(size, row + high)));
    }

    /**
     * {@code line} as wide as {@code width}, if its last span is blank: the padding of a changed line, of the bar of a
     * file or of the tint of the cursor, lengthened so its background goes on to the edge of the area (ADR 80).
     */
    public static Line filled(Line line, int width) {
        List<Span> spans = List.ofAll(line.spans());
        int missing = width - line.width();
        return spans.lastOption()
                .filter(last -> missing > 0 && last.content().isBlank())
                .map(last -> Line.from(spans.init()
                        .append(new Span(last.content() + " ".repeat(missing), last.style()))
                        .toJavaList()))
                .getOrElse(line);
    }

    /**
     * The lines {@link #windowed} shows: from {@code top} up to, not with, {@code bottom}.
     */
    public record Window(int top, int bottom) {}
}
