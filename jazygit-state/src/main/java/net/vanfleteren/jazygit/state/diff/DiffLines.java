package net.vanfleteren.jazygit.state.diff;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.state.diff.DiffStops.HunkAt;

/**
 * The lines of a diff the cursor of the rich view stops on, in the order of the diff (ADR 60): each line of a hunk
 * that has a side and a number, with its hunk. That is the order the rich view draws the hunk a range is open in,
 * which it otherwise draws in pairs where it can (ADR 83). It is what a key of the review needs to start a thread on a
 * range of lines, or to quote the lines of one: the threads are not placed for it, as the stops between two lines
 * that are not lines are left out of a range anyway.
 *
 * @param lines the lines, in order
 */
public record DiffLines(Vector<Line> lines) {

    /**
     * The lines of {@code diff}.
     */
    public static DiffLines of(UnifiedDiff diff) {
        return new DiffLines(diff.files()
                .zipWithIndex()
                .flatMap(file -> file._1()
                        .hunks()
                        .zipWithIndex()
                        .flatMap(hunk -> hunk._1()
                                .lines()
                                .flatMap(line -> Anchor.of(file._1().path(), line)
                                        .map(on -> new Line(on, new HunkAt(file._2(), hunk._2()), line)))))
                .toVector());
    }

    /**
     * The lines from {@code one} to {@code other}, both in, in the order of the diff whichever of them comes first;
     * none unless both are lines of it.
     */
    public Option<Vector<Line>> between(Anchor.OnLine one, Anchor.OnLine other) {
        return between(one, other, false);
    }

    /**
     * The lines from {@code one} to {@code other}, as {@link #between(Anchor.OnLine, Anchor.OnLine)} gives them; a
     * context line found on the left side, as the split view has it a stop there (ADR 139). Each end keeps the anchor
     * it was asked by. In the {@code split} view, a range from a line to a line of the same side holds that side's
     * lines only, its context lines by their anchor on that side, as the column of that side shows them.
     */
    public Option<Vector<Line>> between(Anchor.OnLine one, Anchor.OnLine other, boolean split) {
        int from = lines.indexWhere(line -> line.answers(one));
        int to = lines.indexWhere(line -> line.answers(other));
        if (from < 0 || to < 0) {
            return Option.none();
        }
        Vector<Line> held = lines.slice(Math.min(from, to), Math.max(from, to) + 1);
        Vector<Line> ends = held.update(
                        from - Math.min(from, to),
                        held.get(from - Math.min(from, to)).as(one))
                .update(
                        to - Math.min(from, to),
                        held.get(to - Math.min(from, to)).as(other));
        return Option.some(
                split && one.side() == other.side()
                        ? ends.filter(line -> line.on(one.side())).map(line -> line.onSide(one.side()))
                        : ends);
    }

    /**
     * A line.
     *
     * @param anchor the stop it is
     * @param hunk   its hunk, which a range of lines stays in
     * @param line   the line of the diff
     */
    public record Line(Anchor.OnLine anchor, HunkAt hunk, DiffLine line) {

        /**
         * Whether the line is the stop {@code anchor}: by its own anchor, or, a context line, by its number in the
         * old file on the left side, where the split view has it a stop too (ADR 139).
         */
        boolean answers(Anchor.OnLine anchor) {
            return this.anchor.equals(anchor) || left().contains(anchor);
        }

        /**
         * Whether the line shows on {@code side} of the split view: a context line on both.
         */
        boolean on(ReviewThread.Side side) {
            return line.kind() == DiffLine.Kind.CONTEXT || anchor.side() == side;
        }

        /**
         * The line with its anchor on {@code side}: a context line on the left by its number in the old file.
         */
        Line onSide(ReviewThread.Side side) {
            return side == ReviewThread.Side.LEFT ? left().map(this::as).getOrElse(this) : this;
        }

        /**
         * The line as the stop {@code anchor} it was found by.
         */
        Line as(Anchor.OnLine anchor) {
            return new Line(anchor, hunk, line);
        }

        private Option<Anchor.OnLine> left() {
            return Option.when(line.kind() == DiffLine.Kind.CONTEXT, line::oldNumber)
                    .flatMap(number -> number)
                    .map(number -> new Anchor.OnLine(anchor.path(), ReviewThread.Side.LEFT, number));
        }
    }
}
