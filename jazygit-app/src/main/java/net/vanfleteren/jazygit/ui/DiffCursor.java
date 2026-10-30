package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffStops;

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
 */
record DiffCursor(Vector<RichDiff.Stop> stops, int lines, int height, int stop, int row, Option<Integer> mark) {

    /**
     * The cursor of a diff of {@code lines} lines with {@code stops}, shown {@code height} lines high from
     * {@code row}, on the stop {@code at} is on now ({@link DiffStops#nearest}), or the first, with its range from
     * {@code mark} while that is a line of the hunk of the cursor; the view scrolled so it shows it. None when the diff
     * has no stop.
     */
    static Option<DiffCursor> of(
            Vector<RichDiff.Stop> stops, int lines, int height, int row, Option<Anchor> at, Option<Anchor> mark) {
        Vector<Anchor> anchors = stops.map(stop -> stop.mark().anchor());
        int on = at.flatMap(anchor -> DiffStops.nearest(anchors, anchor))
                .map(anchors::indexOf)
                .getOrElse(0);
        return Option.when(!stops.isEmpty(), () -> {
            DiffCursor cursor = new DiffCursor(stops, lines, Math.max(1, height), on, row, Option.none()).clamped();
            return cursor.withMark(mark.map(anchors::indexOf).filter(index -> index >= 0 && cursor.sameHunk(index)))
                    .shown();
        });
    }

    /**
     * The anchor of the stop the cursor is on.
     */
    Anchor anchor() {
        return stops.get(stop).mark().anchor();
    }

    /**
     * The anchor of the line the range was started on, while one is open.
     */
    Option<Anchor> markAnchor() {
        return mark.map(index -> stops.get(index).mark().anchor());
    }

    /**
     * {@code j}: through the lines of its stop that are below the screen, else to the next stop; the next line of the
     * hunk while a range is open.
     */
    DiffCursor down() {
        if (mark.isDefined()) {
            return Vector.range(stop + 1, stops.size())
                    .find(this::lineOfHunk)
                    .map(next -> at(next, row).shown())
                    .getOrElse(this);
        }
        if (after(stop) > row + height) {
            return at(stop, row + 1);
        }
        if (stop == stops.size() - 1) {
            return this;
        }
        int next = stop + 1;
        return at(next, Math.max(row, Math.min(start(next), after(next) - height)));
    }

    /**
     * {@code k}: through the lines of its stop that are above the screen, else to the stop before; the line before of
     * the hunk while a range is open.
     */
    DiffCursor up() {
        if (mark.isDefined()) {
            return Vector.range(0, stop)
                    .findLast(this::lineOfHunk)
                    .map(before -> at(before, row).shown())
                    .getOrElse(this);
        }
        if (start(stop) < row) {
            return at(stop, row - 1);
        }
        if (stop == 0) {
            return this;
        }
        int before = stop - 1;
        return at(before, Math.min(row, Math.max(start(before), after(before) - height)));
    }

    /**
     * PageDown: a page down, the cursor pulled along to the first stop on the screen if it leaves it.
     */
    DiffCursor pageDown() {
        return scrolled(height);
    }

    /**
     * PageUp: a page up, the cursor pulled along to the last stop on the screen if it leaves it.
     */
    DiffCursor pageUp() {
        return scrolled(-height);
    }

    /**
     * Home: the first stop, from the top; nothing while a range is open.
     */
    DiffCursor home() {
        return mark.isDefined() ? this : at(0, 0);
    }

    /**
     * End: the last stop, to the bottom; nothing while a range is open.
     */
    DiffCursor end() {
        return mark.isDefined() ? this : at(stops.size() - 1, lines);
    }

    /**
     * Scrolled {@code by} lines, down when positive: the cursor is pulled along to the nearest stop with a line on the
     * screen when it would leave it. While a range is open, the cursor stays, and the view only scrolls as far as it
     * still shows it.
     */
    DiffCursor scrolled(int by) {
        if (mark.isDefined()) {
            return at(stop, Math.clamp(row + by, start(stop) - height + 1, start(stop)));
        }
        DiffCursor moved = at(stop, row + by);
        return moved.onScreen(stop)
                ? moved
                : moved.at(moved.containing(by > 0 ? moved.row : moved.row + height - 1), moved.row);
    }

    /**
     * {@code n}: the next thread, the view following it; none when there is none after the cursor, or a range is
     * open.
     */
    Option<DiffCursor> nextThread() {
        return thread(Vector.range(stop + 1, stops.size()).find(this::isThread));
    }

    /**
     * {@code N}: the thread before the cursor.
     */
    Option<DiffCursor> previousThread() {
        return thread(Vector.range(0, stop).findLast(this::isThread));
    }

    private Option<DiffCursor> thread(Option<Integer> found) {
        return found.filter(ignored -> mark.isEmpty())
                .map(index -> at(index, row).shown());
    }

    /**
     * {@code v}: a range started on the line of the cursor, or dropped when one is open; none when the cursor is not
     * on a line, which no range starts on.
     */
    Option<DiffCursor> marked() {
        return mark.isDefined()
                ? Option.of(withMark(Option.none()))
                : Option.when(stops.get(stop).mark().hunk().isDefined(), () -> withMark(Option.of(stop)));
    }

    private DiffCursor withMark(Option<Integer> mark) {
        return new DiffCursor(stops, lines, height, stop, row, mark);
    }

    /**
     * The indexes of the lines of the range, while one is open: those of its hunk from its first to its last.
     */
    private Vector<Integer> range() {
        return mark.map(from -> Vector.rangeClosed(Math.min(from, stop), Math.max(from, stop))
                        .filter(this::lineOfHunk))
                .getOrElse(Vector.empty());
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
        return new DiffCursor(stops, lines, height, stop, row, mark).clamped();
    }

    private DiffCursor clamped() {
        return new DiffCursor(
                stops,
                lines,
                height,
                Math.clamp(stop, 0, stops.size() - 1),
                Math.clamp(row, 0, Math.max(0, lines - height)),
                mark);
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

    private int after(int index) {
        return index + 1 < stops.size() ? start(index + 1) : Math.max(lines, start(index) + 1);
    }

    /**
     * {@code text} with the stop of the cursor tinted in {@code tint} (ADR 60): a bar or a line as wide as its file,
     * the rule of a thread reversed, the lead not at all; while a range is open, each of its lines, marked {@code ┃}
     * in the gutter as the lines a thread covers are. Only those lines are drawn again.
     */
    Text tinted(Text text, Style tint) {
        RichDiff.Stop on = stops.get(stop);
        Vector<Line> lines = Vector.ofAll(text.lines());
        int first = on.row();
        Vector<Line> drawn = mark.isDefined()
                ? range().foldLeft(lines, (done, index) -> {
                    int at = start(index);
                    return done.update(
                            at, tinted(gutterMarked(done.get(at)), on.mark().width(), tint));
                })
                : switch (on.mark().anchor()) {
                    case Anchor.Lead ignored -> lines;
                    case Anchor.Thread ignored ->
                        List.range(
                                        first,
                                        Math.min(lines.size(), first + on.mark().lines()))
                                .foldLeft(lines, (done, at) -> done.update(at, ruled(done.get(at))));
                    case Anchor.File ignored ->
                        lines.update(first, tinted(lines.get(first), on.mark().width(), tint));
                    case Anchor.OnLine ignored ->
                        lines.update(first, tinted(lines.get(first), on.mark().width(), tint));
                };
        return Text.from(drawn.toJavaList());
    }

    private static Line tinted(Line line, int width, Style tint) {
        int pad = Math.max(0, width - line.width());
        return (pad > 0 ? line.append(Span.raw(" ".repeat(pad))) : line).patchStyle(tint);
    }

    /**
     * {@code line} of a diff, the rule at the end of its gutter made the mark of a line a thread covers.
     */
    private static Line gutterMarked(Line line) {
        List<Span> spans = List.ofAll(line.spans());
        return spans.headOption()
                .filter(gutter -> gutter.content().contains("│"))
                .map(gutter -> Line.from(spans.tail()
                        .prepend(new Span(gutter.content().replace('│', '┃'), gutter.style()))
                        .toJavaList()))
                .getOrElse(line);
    }

    /**
     * {@code line} of a thread, its rule reversed.
     */
    private static Line ruled(Line line) {
        return Line.from(List.ofAll(line.spans())
                .map(span -> isRule(span.content()) ? span.reversed() : span)
                .toJavaList());
    }

    private static boolean isRule(String text) {
        return !text.isBlank() && text.chars().allMatch(c -> c == ' ' || "┃┏┣┗━".indexOf(c) >= 0);
    }
}
