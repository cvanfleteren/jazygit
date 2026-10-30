package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.state.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.LineRemarks;

/**
 * Where the cursor of a rich diff stops, in the order the diff is drawn (ADR 60): what comes before its first file,
 * then for each file its bar, the threads about it, each line of its hunks that has a side and a number with the
 * threads after it, and the threads that cannot be placed. A note, a hunk header and a blank line are no stop: they
 * are part of the stop before them.
 *
 * @param stops the stops, in order
 */
public record DiffStops(List<Stop> stops) {

    /**
     * The stops of {@code diff} with the review threads {@code threads} placed in it, on lines only when
     * {@code onLines} (ADR 55).
     */
    public static DiffStops of(UnifiedDiff diff, List<ReviewThread> threads, boolean onLines) {
        PlacedRemarks placed = PlacedRemarks.place(diff, threads, onLines);
        return new DiffStops(diff.files()
                .zip(placed.files())
                .zipWithIndex()
                .flatMap(file -> file(file._1()._1(), file._1()._2(), file._2()))
                .prepend(new Stop(new Anchor.Lead(), Option.none())));
    }

    private static List<Stop> file(FileDiff file, FileRemarks remarks, int index) {
        return List.of(new Stop(new Anchor.File(file.path()), Option.none()))
                .appendAll(threads(remarks.onFile(), Option.none()))
                .appendAll(file.hunks()
                        .zip(remarks.lines())
                        .zipWithIndex()
                        .flatMap(hunk -> hunk._1()
                                ._1()
                                .lines()
                                .zip(hunk._1()._2())
                                .flatMap(
                                        line -> line(file.path(), line._1(), line._2(), new HunkAt(index, hunk._2())))))
                .appendAll(threads(remarks.unplaced(), Option.none()));
    }

    private static List<Stop> line(String path, UnifiedDiff.DiffLine line, LineRemarks remarks, HunkAt hunk) {
        Option<Anchor.OnLine> anchor = Anchor.of(path, line);
        return anchor.map(on -> new Stop(on, Option.of(hunk))).toList().appendAll(threads(remarks.threads(), anchor));
    }

    private static List<Stop> threads(List<ReviewThread> threads, Option<Anchor.OnLine> line) {
        return threads.map(thread -> new Stop(new Anchor.Thread(thread.id(), line), Option.none()));
    }

    /**
     * The anchors of the stops, in order.
     */
    public List<Anchor> anchors() {
        return stops.map(Stop::anchor);
    }

    /**
     * The stop {@code at} is on now, if any: as {@link #nearest} finds it among these stops.
     */
    public Option<Stop> find(Anchor at) {
        return nearest(anchors(), at)
                .flatMap(found -> stops.find(stop -> stop.anchor().equals(found)));
    }

    /**
     * Which of {@code stops} the cursor on {@code at} is on (ADR 60): {@code at} itself if it is one of them, a thread
     * found by its id wherever it is now. When it is gone: a line goes to the line of the same file and side with the
     * closest number, else to the bar of its file, else to the first stop; a thread to the line it was after, and from
     * there as a line; a bar and the lead to the first stop. None when there is no stop.
     */
    public static Option<Anchor> nearest(Seq<Anchor> stops, Anchor at) {
        Option<Anchor> found = switch (at) {
            case Anchor.Thread(String id, Option<Anchor.OnLine> line) ->
                stops.find(stop -> stop instanceof Anchor.Thread(String other, var ignored) && other.equals(id))
                        .orElse(() -> line.flatMap(on -> nearest(stops, on)));
            case Anchor.OnLine(String path, ReviewThread.Side side, int number) ->
                stops.find(at::equals)
                        .orElse(() -> stops.filter(stop -> stop instanceof Anchor.OnLine(var p, var s, var ignored)
                                        && p.equals(path)
                                        && s == side)
                                .minBy(stop -> Math.abs(((Anchor.OnLine) stop).number() - number)))
                        .orElse(() -> stops.find(new Anchor.File(path)::equals));
            case Anchor.File file -> stops.find(file::equals);
            case Anchor.Lead lead -> stops.find(lead::equals);
        };
        return found.orElse(stops::headOption);
    }

    /**
     * A stop.
     *
     * @param anchor what it is
     * @param hunk   the hunk of a line, which a range of lines stays in; none for what is not a line
     */
    public record Stop(Anchor anchor, Option<HunkAt> hunk) {}

    /**
     * A hunk of a diff: the index of its file, and its index in that file.
     */
    public record HunkAt(int file, int hunk) {}
}
