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
 * cursor never leaves the screen: a scroll that would take it off pulls it along.
 *
 * @param stops  where the stops start, in order
 * @param lines  how many lines the diff has
 * @param height how many lines the view shows
 * @param stop   the index of the stop the cursor is on
 * @param row    the first line the view shows
 */
record DiffCursor(Vector<RichDiff.Stop> stops, int lines, int height, int stop, int row) {

    /**
     * The cursor of a diff of {@code lines} lines with {@code stops}, shown {@code height} lines high from
     * {@code row}, on the stop {@code at} is on now ({@link DiffStops#nearest}), or the first; the view scrolled so it
     * shows it. None when the diff has no stop.
     */
    static Option<DiffCursor> of(Vector<RichDiff.Stop> stops, int lines, int height, int row, Option<Anchor> at) {
        Vector<Anchor> anchors = stops.map(stop -> stop.mark().anchor());
        int on = at.flatMap(anchor -> DiffStops.nearest(anchors, anchor))
                .map(anchors::indexOf)
                .getOrElse(0);
        return Option.when(
                !stops.isEmpty(),
                () -> new DiffCursor(stops, lines, Math.max(1, height), on, row)
                        .clamped()
                        .shown());
    }

    /**
     * The anchor of the stop the cursor is on.
     */
    Anchor anchor() {
        return stops.get(stop).mark().anchor();
    }

    /**
     * {@code j}: through the lines of its stop that are below the screen, else to the next stop.
     */
    DiffCursor down() {
        if (end(stop) > row + height) {
            return at(stop, row + 1);
        }
        if (stop == stops.size() - 1) {
            return this;
        }
        int next = stop + 1;
        return at(next, Math.max(row, Math.min(start(next), end(next) - height)));
    }

    /**
     * {@code k}: through the lines of its stop that are above the screen, else to the stop before.
     */
    DiffCursor up() {
        if (start(stop) < row) {
            return at(stop, row - 1);
        }
        if (stop == 0) {
            return this;
        }
        int before = stop - 1;
        return at(before, Math.min(row, Math.max(start(before), end(before) - height)));
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
     * Home: the first stop, from the top.
     */
    DiffCursor home() {
        return at(0, 0);
    }

    /**
     * End: the last stop, to the bottom.
     */
    DiffCursor end() {
        return at(stops.size() - 1, lines);
    }

    /**
     * Scrolled {@code by} lines, down when positive: the cursor is pulled along to the nearest stop with a line on the
     * screen when it would leave it.
     */
    DiffCursor scrolled(int by) {
        DiffCursor moved = at(stop, row + by);
        return moved.onScreen(stop)
                ? moved
                : moved.at(moved.containing(by > 0 ? moved.row : moved.row + height - 1), moved.row);
    }

    private DiffCursor at(int stop, int row) {
        return new DiffCursor(stops, lines, height, stop, row).clamped();
    }

    private DiffCursor clamped() {
        return new DiffCursor(
                stops,
                lines,
                height,
                Math.clamp(stop, 0, stops.size() - 1),
                Math.clamp(row, 0, Math.max(0, lines - height)));
    }

    /**
     * The view scrolled so that it shows a line of the stop of the cursor: as much of it as fits.
     */
    private DiffCursor shown() {
        if (onScreen(stop)) {
            return this;
        }
        return start(stop) >= row + height
                ? at(stop, Math.min(start(stop), end(stop) - height))
                : at(stop, Math.max(start(stop), end(stop) - height));
    }

    private boolean onScreen(int index) {
        return start(index) < row + height && end(index) > row;
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

    private int end(int index) {
        return index + 1 < stops.size() ? start(index + 1) : Math.max(lines, start(index) + 1);
    }

    /**
     * {@code lines} with the stop of the cursor tinted in {@code tint} (ADR 60): a bar or a line as wide as its file,
     * the rule of a thread reversed, the lead not at all. Only those lines are drawn again.
     */
    Text tinted(Text text, Style tint) {
        RichDiff.Stop on = stops.get(stop);
        Vector<Line> lines = Vector.ofAll(text.lines());
        int first = on.row();
        Vector<Line> drawn = switch (on.mark().anchor()) {
            case Anchor.Lead ignored -> lines;
            case Anchor.Thread ignored ->
                List.range(first, Math.min(lines.size(), first + on.mark().lines()))
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
