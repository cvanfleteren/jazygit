package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.state.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.LineRemarks;

/**
 * Where the cursor of a rich diff stops, in the order the diff is drawn (ADR 60): what comes before its first file,
 * then for each file its bar, the threads about it, each line of its hunks that has a side and a number with the
 * threads after it, and the threads that cannot be placed; each comment of an open thread but its first is a stop of
 * its own (ADR 62). A note, a hunk header and a blank line are no stop: they are part of the stop before them.
 *
 * @param stops the stops, in order
 */
public record DiffStops(Vector<Stop> stops) {

    /**
     * The stops of {@code diff} with the review threads {@code threads} placed in it, on lines only when
     * {@code onLines} (ADR 55), each comment of a thread a stop of its own but while it is {@code collapsed} (ADR 62).
     */
    public static DiffStops of(UnifiedDiff diff, List<ReviewThread> threads, boolean onLines, Collapsed collapsed) {
        PlacedRemarks placed = PlacedRemarks.place(diff, threads, onLines);
        return new DiffStops(diff.files()
                .zip(placed.files())
                .zipWithIndex()
                .flatMap(file -> file(file._1()._1(), file._1()._2(), file._2(), collapsed))
                .prepend(new Stop(new Anchor.Lead(), Option.none(), Option.none()))
                .toVector());
    }

    private static List<Stop> file(FileDiff file, FileRemarks remarks, int index, Collapsed collapsed) {
        return List.of(new Stop(new Anchor.File(file.path()), Option.none(), Option.none()))
                .appendAll(threads(remarks.onFile(), Option.none(), collapsed))
                .appendAll(file.hunks()
                        .zip(remarks.lines())
                        .zipWithIndex()
                        .flatMap(hunk -> hunk._1()
                                ._1()
                                .lines()
                                .zip(hunk._1()._2())
                                .flatMap(line -> line(
                                        file.path(), line._1(), line._2(), new HunkAt(index, hunk._2()), collapsed))))
                .appendAll(threads(remarks.unplaced(), Option.none(), collapsed));
    }

    private static List<Stop> line(
            String path, UnifiedDiff.DiffLine line, LineRemarks remarks, HunkAt hunk, Collapsed collapsed) {
        Option<Anchor.OnLine> anchor = Anchor.of(path, line);
        return anchor.map(on -> new Stop(on, Option.of(hunk), Option.of(line)))
                .toList()
                .appendAll(threads(remarks.threads(), anchor, collapsed));
    }

    /**
     * The stops of {@code threads}, after {@code line} if they are: each thread, then each of its comments but the
     * first while it is open.
     */
    private static List<Stop> threads(List<ReviewThread> threads, Option<Anchor.OnLine> line, Collapsed collapsed) {
        return threads.flatMap(thread ->
                anchors(thread, line, collapsed).map(anchor -> new Stop(anchor, Option.none(), Option.none())));
    }

    /**
     * The anchors of the stops of {@code thread}, after {@code line} if it is: the thread, then each of its comments
     * but the first while it is not {@code collapsed} (ADR 62).
     */
    public static List<Anchor> anchors(ReviewThread thread, Option<Anchor.OnLine> line, Collapsed collapsed) {
        Anchor.Thread anchor = new Anchor.Thread(thread.id(), line);
        return collapsed.thread(thread)
                ? List.of(anchor)
                : Collapsed.replies(thread)
                        .<Anchor>map(comment -> new Anchor.Comment(comment.id(), anchor))
                        .prepend(anchor);
    }

    /**
     * The anchors of the stops, in order.
     */
    public Vector<Anchor> anchors() {
        return stops.map(Stop::anchor);
    }

    /**
     * Where among these stops the one that is {@code anchor} is; -1 when none is.
     */
    public int indexOf(Anchor anchor) {
        return stops.indexWhere(stop -> stop.anchor().equals(anchor));
    }

    /**
     * The stop {@code at} is on now, if any: as {@link #nearest} finds it among these stops.
     */
    public Option<Stop> find(Anchor at) {
        Vector<Anchor> anchors = anchors();
        return nearest(anchors, at).map(found -> stops.get(anchors.indexOf(found)));
    }

    /**
     * Which of {@code stops} the cursor on {@code at} is on (ADR 60): {@code at} itself if it is one of them, a thread
     * or a comment found by its id wherever it is now. When it is gone: a line goes to the line of the same file and
     * side with the closest number, else to the bar of its file, else to the first stop; a thread to the line it was
     * after, and from there as a line; a comment to its thread, and from there as a thread (ADR 62); a bar and the
     * lead to the first stop. None when there is no stop.
     */
    public static Option<Anchor> nearest(Seq<Anchor> stops, Anchor at) {
        Option<Anchor> found = switch (at) {
            case Anchor.Comment(String id, Anchor.Thread thread) ->
                stops.find(stop -> stop instanceof Anchor.Comment(String other, var ignored) && other.equals(id))
                        .orElse(() -> nearest(stops, thread));
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
     * @param line   the line of the diff it is, if it is one
     */
    public record Stop(Anchor anchor, Option<HunkAt> hunk, Option<UnifiedDiff.DiffLine> line) {}

    /**
     * A hunk of a diff: the index of its file, and its index in that file.
     */
    public record HunkAt(int file, int hunk) {}
}
