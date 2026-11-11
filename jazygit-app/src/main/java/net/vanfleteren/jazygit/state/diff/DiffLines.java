package net.vanfleteren.jazygit.state.diff;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.state.Model;
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
     * The lines of {@code diff} as the content panel draws it in the area of the cursor {@code at}: with the lines
     * expanded in that area (ADR 77), folded as the view folds them, so that a range stays in a hunk as shown, or the
     * version of its file shown in place of it (ADR 102). None without a diff.
     */
    public static DiffLines shown(Model model, DiffCursorAt at, Option<DiffText> diff) {
        Option<Expansion> expansion = model.repoView()
                .expanded()
                .get(at.area())
                .filter(kept -> kept.holdsFor(at.at().content()));
        Option<Version> version = Version.in(model, at.area(), at.at().content());
        return diff.map(text -> expansion
                        .map(expanded -> expanded.folded(
                                text.parsed(), model.preferences().context()))
                        .getOrElse(() -> Expansion.Folded.of(text.parsed())))
                .map(folded -> version.map(shown -> shown.applied(folded)).getOrElse(folded))
                .map(folded -> of(folded.diff().diff()))
                .getOrElse(() -> new DiffLines(Vector.empty()));
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
