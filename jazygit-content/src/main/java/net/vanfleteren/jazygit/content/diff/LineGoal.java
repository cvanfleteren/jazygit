package net.vanfleteren.jazygit.content.diff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.state.diff.Anchor;

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
    static LineGoal of(Vector<RichDiff.Stop> stops, Vector<RichDiff.Place> places, int at, Side side, int number) {
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
    static boolean numbered(Vector<RichDiff.Stop> stops, Vector<RichDiff.Place> places, int at) {
        return pathAt(stops, at)
                .exists(path -> hasLines(stops, places, path, Side.RIGHT) || hasLines(stops, places, path, Side.LEFT));
    }

    /**
     * The line of a diff or a version shown as source: the stop of the line, else the lines around it shown when a
     * marker of the new file leaves it out, else the closest line.
     */
    private static LineGoal onStops(Vector<RichDiff.Stop> stops, String path, Side side, int number) {
        Vector<Tuple2<Integer, Integer>> lines = stops.zipWithIndex()
                .flatMap(stop -> number(stop._1().mark(), path, side).map(its -> Tuple.of(its, stop._2())));
        Option<Integer> exact = lines.find(line -> line._1() == number).map(Tuple2::_2);
        if (exact.isDefined()) {
            return new OnStop(exact.get(), true, number);
        }
        Tuple2<Integer, Integer> closest =
                lines.minBy(line -> Math.abs(line._1() - number)).getOrElse(Tuple.of(number, 0));
        Option<Anchor.Gap> hidden = Option.when(
                        side == Side.RIGHT,
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
                        closest._2()))
                .getOrElse(() -> new OnStop(closest._2(), false, closest._1()));
    }

    /**
     * The line of a version rendered: the first row of the block or node drawn from it, else from the last line
     * before it that is drawn, as one in a closed node, else from the first.
     */
    private static LineGoal inRow(Vector<RichDiff.Place> places, String path, Side side, int number) {
        Vector<Tuple2<Integer, Integer>> lines =
                places.flatMap(place -> line(place.anchor(), path, side).map(its -> Tuple.of(its, place.row())));
        return lines.find(line -> line._1() == number)
                .map(line -> new InRow(line._2(), true, line._1()))
                .orElse(() -> lines.filter(line -> line._1() < number)
                        .maxBy(Tuple2::_1)
                        .orElse(() -> lines.minBy(Tuple2::_1))
                        .map(line -> new InRow(line._2(), false, line._1())))
                .<LineGoal>map(row -> row)
                .getOrElse(Nowhere::new);
    }

    /**
     * The number of the line of a stop {@code mark} in the file with the path {@code path} on {@code side}: by its
     * anchor, or for a context line by its other anchor (ADR 139).
     */
    private static Option<Integer> number(RichDiff.Mark mark, String path, Side side) {
        return line(mark.anchor(), path, side).orElse(() -> mark.also().flatMap(also -> line(also, path, side)));
    }

    /**
     * The number of the line {@code anchor} is, if it is one of the file with the path {@code path} on {@code side}.
     */
    private static Option<Integer> line(Anchor anchor, String path, Side side) {
        return anchor instanceof Anchor.OnLine(String its, Side on, int number) && its.equals(path) && on == side
                ? Option.some(number)
                : Option.none();
    }

    /**
     * Whether the file with the path {@code path} has a line on {@code side} among {@code stops} or {@code places}.
     */
    private static boolean hasLines(
            Vector<RichDiff.Stop> stops, Vector<RichDiff.Place> places, String path, Side side) {
        return places.isEmpty()
                ? stops.exists(stop -> number(stop.mark(), path, side).isDefined())
                : places.exists(place -> line(place.anchor(), path, side).isDefined());
    }

    /**
     * The path of the file the cursor is in on the stop at {@code at}: that of the stop, or of the closest stop
     * before it with one; on the lead of a commit, that of its first file.
     */
    private static Option<String> pathAt(Vector<RichDiff.Stop> stops, int at) {
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
    private static Option<String> path(Anchor anchor) {
        return switch (anchor) {
            case Anchor.Lead _ -> Option.none();
            case Anchor.File(String path) -> Option.some(path);
            case Anchor.OnLine(String path, var _, var _) -> Option.some(path);
            case Anchor.Gap(String path, var _, var _) -> Option.some(path);
            case Anchor.Node(String path, var _, var _) -> Option.some(path);
            case Anchor.Thread(var _, Option<Anchor.OnLine> line) -> line.map(Anchor.OnLine::path);
            case Anchor.Comment(var _, Anchor.Thread thread) -> path(thread);
        };
    }

    private static Side other(Side side) {
        return side == Side.LEFT ? Side.RIGHT : Side.LEFT;
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
    record Hidden(Anchor.OnLine line, Anchor.Gap lines, int closest) implements LineGoal {}

    /**
     * The row {@code row} of a version rendered, drawn from line {@code number}: the line asked for when
     * {@code exact}, else the closest one drawn.
     */
    record InRow(int row, boolean exact, int number) implements LineGoal {}

    /**
     * Nowhere: what is shown has no lines of a file.
     */
    record Nowhere() implements LineGoal {}
}
