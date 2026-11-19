package net.vanfleteren.jazygit.ui.content;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.ui.text.TextWidth;

/**
 * Where the two halves of a row of the split view are in the line it is drawn as (ADR 139). The line holds the whole
 * code of each half, so the tint of the cursor and the matches of a search patch it where its code is; the area cuts
 * each half to its width when it shows, scrolled sideways by as many columns, its gutter staying.
 *
 * @param left       how many columns the left half takes, up to the rule between the halves
 * @param right      how many columns the right half takes, after that rule
 * @param leftCode   the column of the line its left code starts at, after the gutter and the marker of the half
 * @param leftWidth  how wide the left code is: the rule follows it in the line
 * @param rightCode  the column of the line its right code starts at
 * @param rightWidth how wide the right code is
 */
public record Halves(int left, int right, int leftCode, int leftWidth, int rightCode, int rightWidth) {

    private static final String CUT_BEFORE = "‹";
    private static final String CUT_AFTER = "›";

    /**
     * The column of the line the rule between the halves is at.
     */
    public int rule() {
        return leftCode + leftWidth;
    }

    /**
     * How many columns the code of the left half shows.
     */
    public int leftShown() {
        return Math.max(0, left - leftCode);
    }

    /**
     * How many columns the code of the right half shows.
     */
    public int rightShown() {
        return Math.max(0, right - (rightCode - rule() - 1));
    }

    /**
     * How many columns the halves can be scrolled sideways, until the end of the wider of their codes shows after the
     * {@code ‹} that then marks the code cut off before it.
     */
    public int overflow() {
        return Math.max(over(leftWidth, leftShown()), over(rightWidth, rightShown()));
    }

    private static int over(int width, int shown) {
        return width > shown ? width - shown + 1 : 0;
    }

    /**
     * {@code line} as the area shows it scrolled {@code column} columns sideways: each half cut to its width, its
     * code from that column on, a {@code ‹} at its start when code is cut off there and a {@code ›} at its end when
     * code is cut off there, padded in the style of the marker before it.
     */
    public Line shown(Line line, int column) {
        List<Span> spans = List.ofAll(line.spans());
        int rule = rule();
        return Line.from(slice(spans, 0, leftCode)
                .appendAll(code(
                        slice(spans, leftCode, rule), leftWidth, column, leftShown(), styleAt(spans, leftCode - 1)))
                .appendAll(slice(spans, rule, rightCode))
                .appendAll(code(
                        slice(spans, rightCode, rightCode + rightWidth),
                        rightWidth,
                        column,
                        rightShown(),
                        styleAt(spans, rightCode - 1)))
                .toJavaList());
    }

    /**
     * The code of a half, {@code width} wide, as it shows {@code shown} columns wide from {@code column} on: after a
     * {@code ‹} when it is scrolled, before a {@code ›} when more follows.
     */
    private static List<Span> code(List<Span> code, int width, int column, int shown, Style pad) {
        if (shown <= 0) {
            return List.empty();
        }
        boolean cutBefore = column > 0 && width > 0;
        int after = cutBefore ? shown - 1 : shown;
        boolean cutAfter = width - column > after;
        int room = Math.max(0, cutAfter ? after - 1 : after);
        List<Span> fitted = slice(sideways(code, column), 0, room);
        int used = fitted.map(Span::width).sum().intValue();
        return (cutBefore ? List.of(new Span(CUT_BEFORE, pad)) : List.<Span>empty())
                .appendAll(fitted)
                .append(new Span(" ".repeat(Math.max(0, room - used)), pad))
                .appendAll(cutAfter ? List.of(new Span(CUT_AFTER, pad)) : List.empty());
    }

    /**
     * The style of the span at column {@code column} of {@code spans}; none before the first.
     */
    private static Style styleAt(List<Span> spans, int column) {
        return column < 0
                ? Style.EMPTY
                : spans.foldLeft(
                                Tuple.of(0, Style.EMPTY),
                                (done, span) ->
                                        done._1() <= column ? Tuple.of(done._1() + span.width(), span.style()) : done)
                        ._2();
    }

    /**
     * {@code spans} without their first {@code column} columns.
     */
    static List<Span> sideways(List<Span> spans, int column) {
        return slice(spans, column, Integer.MAX_VALUE);
    }

    /**
     * The columns of {@code spans} from {@code from} up to {@code until}, their spans kept in their styles. A
     * character cut in two leaves a space for each of its columns that is kept.
     */
    public static List<Span> slice(List<Span> spans, int from, int until) {
        if (until <= from) {
            return List.empty();
        }
        return spans.foldLeft(Tuple.of(0, List.<Span>empty()), (done, span) -> {
                    int start = done._1();
                    int end = start + span.width();
                    if (end <= from || start >= until) {
                        return Tuple.of(end, done._2());
                    }
                    String rest = TextWidth.rest(span.content(), Math.max(0, from - start));
                    String kept = until >= end ? rest : TextWidth.start(rest, until - Math.max(from, start));
                    return Tuple.of(end, done._2().prepend(new Span(kept, span.style())));
                })
                ._2()
                .reverse()
                .filter(span -> !span.content().isEmpty());
    }

    /**
     * {@code line} with its columns from {@code from} up to {@code until} patched in {@code style}: the half of a row of
     * the split view the cursor is on.
     */
    public static Line patched(Line line, int from, int until, Style style) {
        List<Span> spans = List.ofAll(line.spans());
        return Line.from(slice(spans, 0, from)
                .appendAll(slice(spans, from, until)
                        .map(span -> new Span(span.content(), span.style().patch(style))))
                .appendAll(slice(spans, until, Integer.MAX_VALUE))
                .toJavaList());
    }
}
