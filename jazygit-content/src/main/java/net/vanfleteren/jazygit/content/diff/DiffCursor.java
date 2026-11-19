package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.ui.text.Rows;

/**
 * The cursor of a rich diff (ADR 60): which of its stops it is on, and where the view is scrolled to, as the keys and
 * the wheel move them. Each stop holds its lines and those after it that are no stop, up to the next stop; one whose
 * lines are not all on the screen is scrolled through before the cursor moves on, so every line can be reached. The
 * cursor never leaves the screen: a scroll that would take it off pulls it along. While a range is open, from the
 * line {@code mark} is on, the cursor only moves over the lines of its hunk.
 *
 * @param stops  where the stops start, in order
 * @param lines  how many lines the diff has
 * @param height how many lines the view shows
 * @param stop   the index of the stop the cursor is on
 * @param row    the first line the view shows
 * @param mark   the index of the line the range was started on, while one is open
 * @param side   the side of the split view the cursor keeps to (ADR 139): that of its stop when it is on a half, else
 *               the side it was on before; only {@code [}, {@code ]}, a click on a half and a match of a search take it
 *               to the other side
 */
public record DiffCursor(
        Vector<Drawn.Stop> stops,
        int lines,
        int height,
        int stop,
        int row,
        Option<Integer> mark,
        ReviewThread.Side side) {

    /**
     * The cursor of a diff of {@code lines} lines with {@code stops}, shown {@code height} lines high from
     * {@code row}, on the stop {@code at} is on now ({@link DiffStops#nearest}), or the first, with its range from
     * {@code mark} while that is a line of the hunk of the cursor; the view scrolled so it shows it. None when the diff
     * has no stop.
     *
     * @param near the index of a stop the cursor was on, which its stop is looked for from ({@link #index}): a frame
     *             of a long diff then finds the stop the cursor is on without going through all of them
     */
    public static Option<DiffCursor> of(
            Vector<Drawn.Stop> stops,
            int near,
            int lines,
            int height,
            int row,
            Option<Anchor> at,
            Option<Anchor> mark) {
        return of(stops, near, lines, height, row, at, mark, ReviewThread.Side.RIGHT);
    }

    /**
     * The cursor as {@link #of(Vector, int, int, int, int, Option, Option)}, on {@code side} of the split view while it
     * is on no half (ADR 139): the side it was on when it was last drawn, the new file's for a diff shown anew.
     */
    public static Option<DiffCursor> of(
            Vector<Drawn.Stop> stops,
            int near,
            int lines,
            int height,
            int row,
            Option<Anchor> at,
            Option<Anchor> mark,
            ReviewThread.Side side) {
        int on = at.flatMap(anchor -> index(stops, anchor, near)
                        .orElse(() -> DiffStops.nearest(
                                        stops.map(stop -> stop.mark().anchor()), anchor)
                                .flatMap(nearest -> index(stops, nearest, near))))
                .getOrElse(0);
        return Option.when(!stops.isEmpty(), () -> {
            DiffCursor cursor =
                    new DiffCursor(stops, lines, Math.max(1, height), on, row, Option.none(), side).clamped();
            // A range keeps to the side it was started on (ADR 139).
            return cursor.withMark(mark.flatMap(anchor -> index(stops, anchor, cursor.stop()))
                            .filter(index -> cursor.sameHunk(index) && cursor.keepsSide(index)))
                    .shown();
        });
    }

    /**
     * The index of the stop of {@code anchor} in {@code stops}, looked for from the stop at {@code near} outwards, the
     * one before it first; the closest, should two have the same. The cursor and a range stay near the stop they were
     * on, so a long diff keeps no table of where each of its stops is (a stop a line).
     */
    public static Option<Integer> index(Vector<Drawn.Stop> stops, Anchor anchor, int near) {
        int from = Math.clamp(near, 0, Math.max(0, stops.size() - 1));
        // A loop, not a recursion over how far away, which a stop missing from a long diff would take as deep as it
        // has stops; and four times as fast as an iterator of the indexes, for a jump across the diff.
        for (int away = 0; away < stops.size(); away++) {
            if (from >= away && stops.get(from - away).mark().is(anchor)) {
                return Option.some(from - away);
            }
            if (away > 0
                    && from + away < stops.size()
                    && stops.get(from + away).mark().is(anchor)) {
                return Option.some(from + away);
            }
        }
        return Option.none();
    }

    /**
     * The anchor of the stop the cursor is on.
     */
    public Anchor anchor() {
        return stops.get(stop).mark().anchor();
    }

    /**
     * Whether the cursor is on a half of a row of the split view (ADR 139): as the diff was drawn, not as its style
     * asked, as the split view draws a file unified where it cannot pair its lines, and all of them where it is too
     * narrow.
     */
    public boolean onHalf() {
        return stops.get(stop).mark().side().isDefined();
    }

    /**
     * The anchor of the line the range was started on, while one is open.
     */
    public Option<Anchor> markAnchor() {
        return mark.map(index -> stops.get(index).mark().anchor());
    }

    /**
     * {@code j}: through the lines of its stop that are below the screen, else to the next stop; the next line of the
     * hunk while a range is open.
     */
    public DiffCursor down() {
        if (mark.isDefined()) {
            return Iterator.range(stop + 1, stops.size())
                    .find(index -> lineOfHunk(index) && keepsSide(index))
                    .map(next -> at(next, row).shown())
                    .getOrElse(this);
        }
        if (after(stop) > row + height) {
            return at(stop, row + 1);
        }
        // In the split view, the next stop on the side of the cursor (ADR 139).
        return Iterator.range(stop + 1, stops.size())
                .find(this::keepsSide)
                .map(next -> at(next, Math.max(row, Math.min(start(next), after(next) - height))))
                .getOrElse(this);
    }

    /**
     * {@code k}: through the lines of its stop that are above the screen, else to the stop before; the line before of
     * the hunk while a range is open.
     */
    public DiffCursor up() {
        if (mark.isDefined()) {
            return Iterator.rangeBy(stop - 1, -1, -1)
                    .find(index -> lineOfHunk(index) && keepsSide(index))
                    .map(before -> at(before, row).shown())
                    .getOrElse(this);
        }
        if (start(stop) < row) {
            return at(stop, row - 1);
        }
        return Iterator.rangeBy(stop - 1, -1, -1)
                .find(this::keepsSide)
                .map(before -> at(before, Math.min(row, Math.max(start(before), after(before) - height))))
                .getOrElse(this);
    }

    /**
     * {@code [} or {@code ]} in the split view (ADR 139): the cursor to the line on {@code side} of its row, or the
     * closest row of its hunk that has one there; where it is when its hunk has none, and while a range is open,
     * which keeps to its side. None off a line, and in the unified view.
     */
    public Option<DiffCursor> toSide(ReviewThread.Side side) {
        Drawn.Mark on = stops.get(stop).mark();
        if (on.side().isEmpty() || !(on.anchor() instanceof Anchor.OnLine)) {
            return Option.none();
        }
        if (mark.isDefined() || on.side().contains(side)) {
            return Option.some(this);
        }
        int at = start(stop);
        // Only within its hunk, between the bars and the markers around it: a row pairs lines of one hunk only.
        int first = Iterator.rangeBy(stop, -1, -1).find(this::bounds).getOrElse(0);
        int last = Iterator.range(stop, stops.size()).find(this::bounds).getOrElse(stops.size() - 1);
        return Option.some(Iterator.rangeClosed(0, Math.max(stop - first, last - stop))
                .flatMap(away -> List.of(stop - away, stop + away))
                .filter(index -> index >= first && index <= last)
                .filter(index -> stops.get(index).mark().side().contains(side)
                        && stops.get(index).mark().anchor() instanceof Anchor.OnLine
                        && sameHunk(index))
                // Outwards from the cursor, so the first found on a row as close is the closest by index too.
                .take(4)
                .toList()
                .minBy(index -> Math.abs(start(index) - at))
                .map(index -> at(index, row).shown())
                // A hunk without a line on that side, as that of an added file: the cursor stays.
                .getOrElse(this));
    }

    /**
     * Whether the stop at {@code index} is one the cursor stops on as it moves: any in the unified view, and in the
     * split view one as wide as the area, or on the side of the cursor (ADR 139).
     */
    private boolean keepsSide(int index) {
        Option<ReviewThread.Side> its = stops.get(index).mark().side();
        return its.isEmpty() || its.contains(side);
    }

    /**
     * PageDown: a page down, the cursor pulled along to the first stop on the screen if it leaves it.
     */
    public DiffCursor pageDown() {
        return scrolled(height);
    }

    /**
     * PageUp: a page up, the cursor pulled along to the last stop on the screen if it leaves it.
     */
    public DiffCursor pageUp() {
        return scrolled(-height);
    }

    /**
     * Home: the first stop, from the top; nothing while a range is open.
     */
    public DiffCursor home() {
        return mark.isDefined() ? this : at(0, 0);
    }

    /**
     * End: the last stop, to the bottom; nothing while a range is open.
     */
    public DiffCursor end() {
        return mark.isDefined() ? this : at(stops.size() - 1, lines);
    }

    /**
     * Scrolled {@code by} lines, down when positive: the cursor is pulled along to the nearest stop with a line on the
     * screen when it would leave it, in the split view one it keeps to the side of (ADR 139). While a range is open,
     * the cursor stays, and the view only scrolls as far as it still shows it.
     */
    public DiffCursor scrolled(int by) {
        if (mark.isDefined()) {
            return at(stop, Math.clamp(row + by, start(stop) - height + 1, start(stop)));
        }
        DiffCursor moved = at(stop, row + by);
        return moved.onScreen(stop) ? moved : moved.pulled(by > 0);
    }

    /**
     * The cursor on the stop with a line on the screen nearest its top when {@code down}, else its bottom, of those
     * it keeps to the side of; the nearest of any when none is.
     */
    private DiffCursor pulled(boolean down) {
        int top = containing(row);
        int bottom = containing(Math.min(row + height, lines) - 1);
        // Both halves of the row at the top, the first of which comes before the stop that row is found by.
        int first = Iterator.rangeBy(top, -1, -1)
                .takeWhile(index -> start(index) == start(top))
                .last();
        Iterator<Integer> nearest =
                down ? Iterator.rangeClosed(first, bottom) : Iterator.rangeBy(bottom, first - 1, -1);
        return at(nearest.find(this::keepsSide).getOrElse(down ? top : bottom), row);
    }

    /**
     * {@code n}: the next thread, the view following it, the first after the last; none when there is no other
     * thread, or a range is open.
     */
    public Option<DiffCursor> nextThread() {
        return thread(Vector.range(stop + 1, stops.size())
                .appendAll(Vector.range(0, stop))
                .find(this::isThread));
    }

    /**
     * {@code N}: the thread before the cursor, the last before the first.
     */
    public Option<DiffCursor> previousThread() {
        return thread(Vector.range(stop + 1, stops.size())
                .appendAll(Vector.range(0, stop))
                .findLast(this::isThread));
    }

    /**
     * A click on the {@code line}th line the view shows: the stop that line is part of, the view staying where it is;
     * while a range is open, only a line of its hunk, which the range then reaches to. None for a line the view does
     * not show, or one past the end of the diff.
     */
    public Option<DiffCursor> clicked(int line) {
        return clicked(line, 0, Option.none());
    }

    /**
     * A click on column {@code column} of the {@code line}th line the view shows, as {@link #clicked(int)}: in the
     * split view, whose halves meet at column {@code divider}, the line of the half clicked on a row, none on a half
     * without a line (ADR 139).
     */
    public Option<DiffCursor> clicked(int line, int column, Option<Integer> divider) {
        return Option.when(line >= 0 && line < height && row + line < lines, () -> containing(row + line))
                .flatMap(index -> divider.map(at ->
                                half(index, row + line, column < at ? ReviewThread.Side.LEFT : ReviewThread.Side.RIGHT))
                        .getOrElse(Option.some(index)))
                .filter(index -> mark.isEmpty() || (lineOfHunk(index) && keepsSide(index)))
                .map(index -> at(index, row));
    }

    /**
     * The stop of the half on {@code side} of the row {@code line}, whose last stop is at {@code index}: none when the
     * half has no line; the stop at {@code index} itself when the row is no row of lines.
     */
    private Option<Integer> half(int index, int line, ReviewThread.Side side) {
        boolean ofLines = start(index) == line
                && stops.get(index).mark().side().isDefined()
                && stops.get(index).mark().anchor() instanceof Anchor.OnLine;
        return ofLines
                ? List.of(index, index - 1)
                        .find(at -> at >= 0
                                && start(at) == line
                                && stops.get(at).mark().side().contains(side))
                : Option.some(index);
    }

    /**
     * {@code n} or {@code N} of a search (ADR 126): the stop line {@code line} is part of, the view scrolled so it
     * shows that line, and as much of the stop as fits besides; while a range is open, only a line of its hunk, which
     * the range then reaches to, as with a click. None for a line outside that hunk.
     */
    public Option<DiffCursor> toLine(int line) {
        return toLine(line, Option.none());
    }

    /**
     * {@code n} or {@code N} of a search, as {@link #toLine(int)}: in the split view, to the half on {@code onSide}
     * of the row, that of the match (ADR 139). While a range is open, only a line on its side.
     */
    public Option<DiffCursor> toLine(int line, Option<ReviewThread.Side> onSide) {
        int last = containing(line);
        int index = onSide.flatMap(its -> half(last, line, its)).getOrElse(last);
        return Option.when(mark.isEmpty() || (lineOfHunk(index) && keepsSide(index)), () -> {
            DiffCursor moved = at(index, row).shown();
            return line >= moved.row && line < moved.row + height
                    ? moved
                    : at(index, line < moved.row ? line : line - height + 1);
        });
    }

    /**
     * The first line of the stop the cursor is on.
     */
    public int stopStart() {
        return start(stop);
    }

    private Option<DiffCursor> thread(Option<Integer> found) {
        return found.filter(ignored -> mark.isEmpty())
                .map(index -> at(index, row).shown());
    }

    /**
     * {@code v}: a range started on the line of the cursor, or dropped when one is open; none when the cursor is not
     * on a line, which no range starts on.
     */
    public Option<DiffCursor> marked() {
        return mark.isDefined()
                ? Option.of(withMark(Option.none()))
                : Option.when(stops.get(stop).mark().hunk().isDefined(), () -> withMark(Option.of(stop)));
    }

    private DiffCursor withMark(Option<Integer> mark) {
        return new DiffCursor(stops, lines, height, stop, row, mark, side);
    }

    /**
     * The indexes of the lines of the range, while one is open: those of its hunk from its first to its last.
     */
    private Vector<Integer> range() {
        return mark.map(from -> stops.get(from).mark().side().isDefined()
                        ? splitRange(from)
                        : Vector.rangeClosed(Math.min(from, stop), Math.max(from, stop))
                                .filter(this::lineOfHunk))
                .getOrElse(Vector.empty());
    }

    /**
     * The indexes of the lines of a range of the split view from the line at {@code from} (ADR 139): those of its
     * hunk on its side from its first to its last, as the cursor keeps to the side the range was started on.
     */
    private Vector<Integer> splitRange(int from) {
        Drawn.Mark start = stops.get(from).mark();
        Drawn.Mark end = stops.get(stop).mark();
        int low = Math.min(start.order(), end.order());
        int high = Math.max(start.order(), end.order());
        // The lines of a hunk lie between the bars and the markers around it.
        int first = Iterator.rangeBy(Math.min(from, stop), -1, -1)
                .find(this::bounds)
                .getOrElse(0);
        int last = Iterator.range(Math.max(from, stop), stops.size())
                .find(this::bounds)
                .getOrElse(stops.size() - 1);
        return Vector.rangeClosed(first, last).filter(index -> {
            Drawn.Mark its = stops.get(index).mark();
            return lineOfHunk(index)
                    && its.order() >= low
                    && its.order() <= high
                    && its.side().equals(start.side());
        });
    }

    private boolean bounds(int index) {
        return stops.get(index).mark().anchor() instanceof Anchor.File
                || stops.get(index).mark().anchor() instanceof Anchor.Gap
                || stops.get(index).mark().anchor() instanceof Anchor.Filtered;
    }

    private boolean lineOfHunk(int index) {
        return stops.get(index).mark().hunk().isDefined() && sameHunk(index);
    }

    private boolean sameHunk(int index) {
        return stops.get(index).mark().hunk().isDefined()
                && stops.get(index).mark().hunk().equals(stops.get(stop).mark().hunk());
    }

    private boolean isThread(int index) {
        return stops.get(index).mark().anchor() instanceof Anchor.Thread;
    }

    private DiffCursor at(int stop, int row) {
        return new DiffCursor(stops, lines, height, stop, row, mark, side).clamped();
    }

    /**
     * The cursor on a stop of the diff, and the view within its lines; on the side of its stop when that is a half.
     */
    private DiffCursor clamped() {
        int on = Math.clamp(stop, 0, stops.size() - 1);
        return new DiffCursor(
                stops,
                lines,
                height,
                on,
                Math.clamp(row, 0, Math.max(0, lines - height)),
                mark,
                stops.get(on).mark().side().getOrElse(side));
    }

    /**
     * The view scrolled so that it shows a line of the stop of the cursor: as much of it as fits.
     */
    private DiffCursor shown() {
        if (onScreen(stop)) {
            return this;
        }
        return start(stop) >= row + height
                ? at(stop, Math.min(start(stop), after(stop) - height))
                : at(stop, Math.max(start(stop), after(stop) - height));
    }

    private boolean onScreen(int index) {
        return start(index) < row + height && after(index) > row;
    }

    /**
     * The index of the stop line {@code line} is part of.
     */
    private int containing(int line) {
        return Math.max(0, stops.lastIndexWhere(stop -> stop.row() <= line));
    }

    private int start(int index) {
        return stops.get(index).row();
    }

    /**
     * The line after the lines of the stop at {@code index}: where the next stop on a later line starts, as both
     * halves of a row of the split view are stops on one line.
     */
    private int after(int index) {
        int from = start(index);
        return Iterator.range(index + 1, stops.size())
                .map(this::start)
                .find(next -> next > from)
                .getOrElse(() -> Math.max(lines, from + 1));
    }

    /**
     * {@code lines} with the stop of the cursor tinted in {@code tint} (ADR 60): a bar or a line as wide as its file,
     * the rule of a thread reversed, the lead not at all; while a range is open, each of its lines, marked {@code ┃}
     * in the gutter as the lines a thread covers are. Only those lines are drawn again, once they show.
     */
    public Rows tinted(Rows lines, Style tint) {
        Drawn.Stop on = stops.get(stop);
        int first = on.row();
        int width = on.mark().width();
        return mark.isDefined()
                ? range().foldLeft(lines, (done, index) -> {
                    Drawn.Mark its = stops.get(index).mark();
                    return its.side().isDefined()
                            ? done.update(
                                    start(index),
                                    line -> Halves.patched(
                                            gutterMarked(line, its.column()),
                                            its.column(),
                                            its.column() + its.width(),
                                            tint))
                            : done.update(start(index), line -> tinted(gutterMarked(line, 0), width, tint));
                })
                : switch (on.mark().anchor()) {
                    case Anchor.Lead ignored -> lines;
                    case Anchor.Thread _, Anchor.Comment _ ->
                        List.range(
                                        first,
                                        Math.min(lines.size(), first + on.mark().lines()))
                                .foldLeft(lines, (done, at) -> done.update(at, DiffCursor::ruled));
                    case Anchor.File ignored -> lines.update(first, line -> tinted(line, width, tint));
                    case Anchor.Gap ignored -> lines.update(first, line -> tinted(line, width, tint));
                    case Anchor.Filtered ignored -> lines.update(first, line -> tinted(line, width, tint));
                    // The half of its line in the split view (ADR 139), which the area pads in its tint.
                    case Anchor.OnLine ignored ->
                        on.mark().side().isDefined()
                                ? lines.update(
                                        first,
                                        line -> Halves.patched(
                                                line,
                                                on.mark().column(),
                                                on.mark().column() + width,
                                                tint))
                                : lines.update(first, line -> tinted(line, width, tint));
                    case Anchor.Node ignored -> lines.update(first, line -> tinted(line, width, tint));
                };
    }

    private static Line tinted(Line line, int width, Style tint) {
        // Padded in a span of its own even when the line is as wide as its file, which the area draws as far as its
        // edge (ADR 80).
        return line.append(Span.raw(" ".repeat(Math.max(0, width - line.width()))))
                .patchStyle(tint);
    }

    /**
     * {@code line} of a diff, the rule at the end of its gutter made the mark of a line a thread covers: the gutter
     * that starts at column {@code from}, that of the half of a row of the split view (ADR 139).
     */
    private static Line gutterMarked(Line line, int from) {
        List<Span> spans = List.ofAll(line.spans());
        List<Integer> starts =
                spans.scanLeft(0, (at, span) -> at + span.width()).init();
        return starts.indexWhere(at -> at >= from) < 0
                ? line
                : Option.of(starts.indexWhere(at -> at >= from))
                        .filter(at -> spans.get(at).content().contains("│"))
                        .map(at -> Line.from(spans.update(
                                        at,
                                        new Span(
                                                spans.get(at).content().replace('│', '┃'),
                                                spans.get(at).style()))
                                .toJavaList()))
                        .getOrElse(line);
    }

    /**
     * {@code line} of a thread, the strokes of its rule reversed, not the spaces around them.
     */
    private static Line ruled(Line line) {
        return Line.from(List.ofAll(line.spans())
                .flatMap(span -> isRule(span.content()) ? strokes(span) : List.of(span))
                .toJavaList());
    }

    private static List<Span> strokes(Span rule) {
        return List.of(rule.content().split("(?<= )(?! )|(?<! )(?= )"))
                .map(part -> part.isBlank() ? new Span(part, rule.style()) : new Span(part, rule.style()).reversed());
    }

    private static boolean isRule(String text) {
        return !text.isBlank() && text.chars().allMatch(c -> c == ' ' || "┃┏┣┗━".indexOf(c) >= 0);
    }
}
