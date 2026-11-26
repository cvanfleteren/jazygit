package net.vanfleteren.jazygit.kernel.content.diff;

import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;

/**
 * Where {@code :} with a line number goes in what an area shows (ADR 140): a line of the file the cursor is in, in the
 * file of the side asked for, or of the other side when the file has no line on that one.
 */
public sealed interface LineGoal {

    /**
     * How many lines above and below a line a marker leaves out are shown with it.
     */
    int AROUND = 5;

    /**
     * Where line {@code number} is, in what has the cursor {@code stops}, the cursor on the stop at {@code at}, and the
     * lines of a version rendered drawn at {@code places} (ADR 103), numbered in the file of {@code side}.
     */
    static LineGoal of(Vector<Drawn.Stop> stops, Vector<Drawn.Place> places, int at, DiffSide side, int number) {
        return pathAt(stops, at)
                .flatMap(path -> List.of(side, other(side))
                        .find(those -> hasLines(stops, places, path, those))
                        .map(those -> places.isEmpty()
                                ? onStops(stops, path, those, number)
                                : inRow(places, path, those, number)))
                .getOrElse(Nowhere::new);
    }

    /**
     * Whether what has {@code stops} and {@code places}, the cursor on the stop at {@code at}, has line numbers to go
     * to: lines of a file, on either side. Asked for each key the area hands on, so it looks no further than the
     * first line it finds.
     */
    static boolean numbered(Vector<Drawn.Stop> stops, Vector<Drawn.Place> places, int at) {
        return pathAt(stops, at)
                .exists(path ->
                        hasLines(stops, places, path, DiffSide.NEW) || hasLines(stops, places, path, DiffSide.OLD));
    }

    /**
     * The line of a diff or a version shown as source: the stop of the line, else the lines around it shown when a
     * marker of the new file leaves it out, else the closest line.
     */
    private static LineGoal onStops(Vector<Drawn.Stop> stops, RepoPath path, DiffSide side, int number) {
        Vector<NumberedRow> lines = stops.zipWithIndex(
                        (stop, index) -> number(stop.mark(), path, side).map(its -> new NumberedRow(its, index)))
                .flatMap(line -> line);
        Option<Integer> exact = lines.find(line -> line.number() == number).map(NumberedRow::row);
        if (exact.isDefined()) {
            return new OnStop(exact.get(), true, number);
        }
        NumberedRow closest =
                lines.minBy(line -> Math.abs(line.number() - number)).getOrElse(new NumberedRow(number, 0));
        // A line of a hunk the diff filters hide is shown by opening its marker (ADR 144).
        Option<Anchor.Filtered> filtered = stops.iterator()
                .map(stop -> stop.mark().anchor())
                .filter(Anchor.Filtered.class::isInstance)
                .map(Anchor.Filtered.class::cast)
                .find(marker -> !marker.opened()
                        && marker.path().equals(path)
                        && marker.hunks().exists(hunk -> hunk.has(side == DiffSide.OLD, number)));
        if (filtered.isDefined()) {
            return new Hidden(new Anchor.OnLine(path, side, number), filtered.get(), closest.row());
        }
        Option<Anchor.Gap> hidden = Option.when(
                        side == DiffSide.NEW,
                        () -> stops.iterator()
                                .map(stop -> stop.mark().anchor())
                                .filter(Anchor.Gap.class::isInstance)
                                .map(Anchor.Gap.class::cast)
                                .find(gap -> gap.path().equals(path)
                                        && gap.from() <= number
                                        && gap.until().forAll(until -> number < until)))
                .flatMap(gap -> gap);
        return hidden.<LineGoal>map(gap -> new Hidden(
                        new Anchor.OnLine(path, side, number),
                        new Anchor.Gap(
                                path,
                                Math.max(gap.from(), number - AROUND),
                                Option.some(gap.until()
                                        .map(until -> Math.min(until, number + AROUND + 1))
                                        .getOrElse(number + AROUND + 1))),
                        closest.row()))
                .getOrElse(() -> new OnStop(closest.row(), false, closest.number()));
    }

    /**
     * The line of a version rendered: the first row of the block or node drawn from it, else from the last line
     * before it that is drawn, as one in a closed node, else from the first.
     */
    private static LineGoal inRow(Vector<Drawn.Place> places, RepoPath path, DiffSide side, int number) {
        Vector<NumberedRow> lines =
                places.flatMap(place -> line(place.anchor(), path, side).map(its -> new NumberedRow(its, place.row())));
        return lines.find(line -> line.number() == number)
                .map(line -> new InRow(line.row(), true, line.number()))
                .orElse(() -> lines.filter(line -> line.number() < number)
                        .maxBy(NumberedRow::number)
                        .orElse(() -> lines.minBy(NumberedRow::number))
                        .map(line -> new InRow(line.row(), false, line.number())))
                .<LineGoal>map(row -> row)
                .getOrElse(Nowhere::new);
    }

    /**
     * The number of the line of a stop {@code mark} in the file with the path {@code path} on {@code side}: by its
     * anchor, or for a context line by its other anchor (ADR 139).
     */
    private static Option<Integer> number(Drawn.Mark mark, RepoPath path, DiffSide side) {
        return line(mark.anchor(), path, side).orElse(() -> mark.also().flatMap(also -> line(also, path, side)));
    }

    /**
     * The number of the line {@code anchor} is, if it is one of the file with the path {@code path} on {@code side}.
     */
    private static Option<Integer> line(Anchor anchor, RepoPath path, DiffSide side) {
        return anchor instanceof Anchor.OnLine(RepoPath its, DiffSide on, int number) && its.equals(path) && on == side
                ? Option.some(number)
                : Option.none();
    }

    /**
     * Whether the file with the path {@code path} has a line on {@code side} among {@code stops} or {@code places}.
     */
    private static boolean hasLines(
            Vector<Drawn.Stop> stops, Vector<Drawn.Place> places, RepoPath path, DiffSide side) {
        return places.isEmpty()
                ? stops.exists(stop -> number(stop.mark(), path, side).isDefined())
                : places.exists(place -> line(place.anchor(), path, side).isDefined());
    }

    /**
     * The path of the file the cursor is in on the stop at {@code at}: that of the stop, or of the closest stop
     * before it with one; on the lead of a commit, that of its first file.
     */
    private static Option<RepoPath> pathAt(Vector<Drawn.Stop> stops, int at) {
        int from = Math.clamp(at, 0, Math.max(0, stops.size() - 1));
        return Iterator.rangeBy(from, -1, -1)
                .concat(Iterator.range(from + 1, stops.size()))
                .filter(index -> index < stops.size())
                .flatMap(index -> path(stops.get(index).mark().anchor()))
                .headOption();
    }

    /**
     * The path of the file {@code anchor} is in, if it is in one.
     */
    private static Option<RepoPath> path(Anchor anchor) {
        return switch (anchor) {
            case Anchor.Lead _ -> Option.none();
            case Anchor.File(RepoPath path) -> Option.some(path);
            case Anchor.OnLine(RepoPath path, var _, var _) -> Option.some(path);
            case Anchor.Gap(RepoPath path, var _, var _) -> Option.some(path);
            case Anchor.Filtered(RepoPath path, var _, var _) -> Option.some(path);
            case Anchor.Node(RepoPath path, var _, var _) -> Option.some(path);
            case Anchor.Thread(var _, Option<Anchor.OnLine> line) -> line.map(Anchor.OnLine::path);
            case Anchor.Remark(var _, Anchor.Thread thread) -> path(thread);
        };
    }

    private static DiffSide other(DiffSide side) {
        return side == DiffSide.OLD ? DiffSide.NEW : DiffSide.OLD;
    }

    /**
     * The stop at {@code index}, of line {@code number}: the line asked for when {@code exact}, else the closest line
     * shown.
     */
    record OnStop(int index, boolean exact, int number) implements LineGoal {}

    /**
     * The line {@code line}, which a marker leaves out: {@code lines} are those around it it leaves out, to show, and
     * the stop at {@code closest} the closest line shown until they are.
     */
    record Hidden(Anchor.OnLine line, Anchor lines, int closest) implements LineGoal {}

    /**
     * The row {@code row} of a version rendered, drawn from line {@code number}: the line asked for when
     * {@code exact}, else the closest one drawn.
     */
    record InRow(int row, boolean exact, int number) implements LineGoal {}

    /**
     * Nowhere: what is shown has no lines of a file.
     */
    record Nowhere() implements LineGoal {}

    /**
     * Line {@code number} of a file, at {@code row}: the index of its stop, or the row a version rendered draws it in.
     */
    record NumberedRow(int number, int row) {}
}
