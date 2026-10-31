package net.vanfleteren.jazygit.state;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.state.DiffStops.HunkAt;

/**
 * The lines of a diff the cursor of the rich view stops on, in the order it draws them (ADR 60): each line of a hunk
 * that has a side and a number, with its hunk. It is what a key of the review needs to start a thread on a range of
 * lines, or to quote the lines of one: the threads are not placed for it, as the stops between two lines that are not
 * lines are left out of a range anyway.
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
        int from = lines.indexWhere(line -> line.anchor().equals(one));
        int to = lines.indexWhere(line -> line.anchor().equals(other));
        return from < 0 || to < 0
                ? Option.none()
                : Option.some(lines.slice(Math.min(from, to), Math.max(from, to) + 1));
    }

    /**
     * A line.
     *
     * @param anchor the stop it is
     * @param hunk   its hunk, which a range of lines stays in
     * @param line   the line of the diff
     */
    public record Line(Anchor.OnLine anchor, HunkAt hunk, DiffLine line) {}
}
