package net.vanfleteren.jazygit.ui.content;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.IntSupplier;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.Rows;

/**
 * The lines of a diff, each drawn when it shows ({@link Rows}), where each stop of the cursor in them starts, in order,
 * and what works out how wide the widest of them is.
 *
 * @param widest  how wide the widest line is, worked out when it is asked for: the lines of a diff are not drawn for
 *                it, as each is no wider than its file; other lines are drawn to be measured, as
 *                {@link Rows#widest} does. In the split view, as wide as the area and as far as its halves scroll
 *                sideways
 * @param halves  where the halves of each row of the split view are, by the index of the row; none for a row as wide
 *                as the area, and none at all in the unified view (ADR 139)
 * @param kept    how many columns at the start of each row stay where they are when the area scrolls sideways, by the
 *                index of the row: all of those of a row of the split view as wide as the area of a file drawn split,
 *                as a line of a file the split view draws unified scrolls as in the unified view (ADR 139); the gutter
 *                of a line of a version shown blamed (ADR 147). None at all when no row keeps any
 * @param divider the column of the area the rule between the halves of the split view is at
 * @param note    what the title of the area says of how the diff is drawn: that the area is too narrow for the split
 *                view
 */
public record Drawn(
        Rows rows,
        List<Stop> stops,
        List<Place> places,
        IntSupplier widest,
        Vector<Option<Halves>> halves,
        Vector<Integer> kept,
        Option<Integer> divider,
        Option<String> note) {

    /**
     * The columns {@link #keptAt} says a row keeps that does not scroll sideways at all, as wide as the area.
     */
    public static final int PINNED = Integer.MAX_VALUE;

    private Drawn(Rows rows, List<Stop> stops) {
        this(rows, stops, List.empty(), rows::widest, Vector.empty(), Vector.empty(), Option.none(), Option.none());
    }

    /**
     * {@code lines}, without a stop: what has no cursor.
     */
    public static Drawn plain(List<Line> lines) {
        return plain(Rows.of(lines));
    }

    /**
     * {@code rows}, without a stop: what has no cursor.
     */
    public static Drawn plain(Rows rows) {
        return new Drawn(rows, List.empty());
    }

    /**
     * All the lines, drawn.
     */
    public List<Line> lines() {
        return rows.all();
    }

    /**
     * Whether the diff is drawn with the old and the new file side by side (ADR 139).
     */
    public boolean split() {
        return divider.isDefined();
    }

    /**
     * Where the halves of row {@code row} of the split view are; none for a row as wide as the area.
     */
    public Option<Halves> halvesOf(int row) {
        return row >= 0 && row < halves.size() ? halves.get(row) : Option.none();
    }

    /**
     * How many columns at the start of row {@code row} stay where they are when the area scrolls sideways: all of
     * those of a row of the split view as wide as the area (ADR 139), the gutter of a line of a version shown blamed
     * (ADR 147), else none.
     */
    public int keptAt(int row) {
        return row >= 0 && row < kept.size() ? kept.get(row) : 0;
    }

    /**
     * These lines after {@code before}, their stops moved down by as many lines.
     */
    public Drawn after(List<Line> before) {
        IntSupplier inBody = widest;
        return new Drawn(
                rows.after(Rows.of(before)),
                stops.map(stop -> new Stop(stop.mark(), stop.row() + before.size())),
                places.map(place -> new Place(place.anchor(), place.row() + before.size())),
                () -> Math.max(inBody.getAsInt(), before.map(Line::width).max().getOrElse(0)),
                halves.isEmpty()
                        ? halves
                        : Vector.<Option<Halves>>fill(before.size(), Option::none)
                                .appendAll(halves),
                kept.isEmpty() ? kept : Vector.fill(before.size(), () -> 0).appendAll(kept),
                divider,
                note);
    }

    /**
     * These lines with what comes before their first stop as a stop of its own, the lead, if anything does.
     */
    public Drawn withLead() {
        return stops.headOption()
                .filter(first -> first.row() > 0)
                .map(first -> new Drawn(
                        rows,
                        stops.prepend(new Stop(new Mark(new Anchor.Lead(), first.row(), 0, Option.none()), 0)),
                        places,
                        widest,
                        halves,
                        kept,
                        divider,
                        note))
                .getOrElse(this);
    }

    /**
     * These lines with the rule between the halves of the split view at column {@code column} of the area.
     */
    public Drawn withDivider(int column) {
        return new Drawn(rows, stops, places, widest, halves, kept, Option.some(column), note);
    }

    /**
     * These lines with {@code says} in the title of the area, after what it says already.
     */
    public Drawn withNote(String says) {
        return new Drawn(
                rows,
                stops,
                places,
                widest,
                halves,
                kept,
                divider,
                Option.some(note.map(before -> Messages.get("panel.notes", before, says))
                        .getOrElse(says)));
    }

    /**
     * A stop of the cursor (ADR 60): what it is, how many lines it has itself, after which the lines that are no stop
     * are part of it too, and how many columns the cursor is tinted on.
     *
     * @param anchor what it is
     * @param lines  how many lines it has itself
     * @param width  how many columns its tint covers: the width of its file, or of its half in the split view
     * @param hunk   the hunk of a line, which a range stays in; none for what is not a line
     * @param side   the side of the split view it is on, the half it is in (ADR 139); none for what is as wide as the
     *               area, and in the unified view
     * @param column the column of its line its tint starts at: that of its half in the split view
     * @param also   the other anchor of a context line, which the stop is found by too: on the left side, by its number
     *               in the old file, in the unified view, where the line is a stop on the right side only (ADR 139)
     * @param order  the index of its line among the lines of its hunk, in the order of the diff; -1 for what is not a
     *               line
     */
    public record Mark(
            Anchor anchor,
            int lines,
            int width,
            Option<DiffStops.HunkAt> hunk,
            Option<ReviewThread.Side> side,
            int column,
            Option<Anchor> also,
            int order) {

        public Mark(Anchor anchor, int lines, int width, Option<DiffStops.HunkAt> hunk) {
            this(anchor, lines, width, hunk, Option.none(), 0, Option.none(), -1);
        }

        /**
         * Whether the stop is {@code anchor}, or a context line it is the other anchor of.
         */
        public boolean is(Anchor anchor) {
            return this.anchor.equals(anchor) || also.contains(anchor);
        }

        /**
         * This stop in the half of {@code side} (ADR 139).
         */
        public Mark onSide(ReviewThread.Side side) {
            return new Mark(anchor, lines, width, hunk, Option.some(side), column, also, order);
        }
    }

    /**
     * Where a stop of the cursor starts.
     *
     * @param mark the stop
     * @param row  the line it starts on
     */
    public record Stop(Mark mark, int row) {}

    /**
     * Where a line of a version rendered is drawn (ADR 103): the first row of the block it is part of. No stop of the
     * cursor, which a version rendered has none of but the bar of its file.
     *
     * @param anchor the line
     * @param row    the first row of its block
     */
    public record Place(Anchor anchor, int row) {}
}
