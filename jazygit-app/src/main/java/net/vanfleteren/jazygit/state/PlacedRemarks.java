package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.gh.model.ThreadsByPath;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;

/**
 * The review threads of a pull request placed in a parsed diff of some of the files it changes (ADR 55): each after
 * the line it is on, about its whole file, or in a list at the end of its file when it cannot be placed.
 *
 * @param files for each file of the diff, in its order, the threads on it
 */
public record PlacedRemarks(List<FileRemarks> files) {

    private static final LineRemarks NONE = new LineRemarks(List.empty(), false);

    /**
     * {@code threads} placed in {@code diff}. A thread goes after the line with its number on its side, in the file
     * with its path: the number in the new file for {@link Side#RIGHT}, in the old one for {@link Side#LEFT}; one on
     * several lines after its last one, covering every line of the diff from its first, on that line's own side, to
     * it, or from the first line the diff shows after its first, when it does not show that. One about a whole file
     * goes under its bar. One that cannot be placed, outdated or on a line the diff does not show, is listed at the end
     * of its file. A thread of a file the diff does not have is left out: it is shown with the diff of its own file.
     */
    public static PlacedRemarks place(UnifiedDiff diff, List<ReviewThread> threads) {
        return place(diff, threads, true);
    }

    /**
     * {@code threads} placed in {@code diff} as {@link #place(UnifiedDiff, List)} does, but on lines only when
     * {@code onLines}: when they were read at other commits than the diff, their lines number other lines, and they
     * are listed at the end of their file (ADR 55).
     */
    public static PlacedRemarks place(UnifiedDiff diff, List<ReviewThread> threads, boolean onLines) {
        ThreadsByPath byPath = ThreadsByPath.of(threads);
        return new PlacedRemarks(diff.files().map(file -> file(file, byPath.on(file.path(), file.origin()), onLines)));
    }

    /**
     * Whether no thread is placed anywhere.
     */
    public boolean isEmpty() {
        return files.forAll(FileRemarks::isEmpty);
    }

    private static FileRemarks file(FileDiff file, List<ReviewThread> threads, boolean placeable) {
        // Most files of a diff have no thread: those need nothing worked out for their lines.
        if (threads.isEmpty()) {
            return new FileRemarks(
                    List.empty(), file.hunks().map(hunk -> hunk.lines().map(ignored -> NONE)), List.empty());
        }
        // The lines of the file in the order of the diff: a line is found by its index among them.
        Vector<DiffLine> lines = file.hunks().flatMap(Hunk::lines).toVector();
        Vector<Integer> starts = file.hunks()
                .scanLeft(0, (start, hunk) -> start + hunk.lines().size())
                .init()
                .toVector();
        List<ReviewThread> onLines = threads.filter(thread -> thread.subject() == Subject.LINE);
        List<Tuple2<ReviewThread, Option<Integer>>> found = onLines.map(thread ->
                Tuple.of(thread, placeable ? at(lines, thread.line(), thread.side()) : Option.<Integer>none()));
        Map<Integer, List<ReviewThread>> after = found.flatMap(
                        placed -> placed._2().map(at -> Tuple.of(at, placed._1())))
                .groupBy(Tuple2::_1)
                .mapValues(placed -> placed.map(Tuple2::_2));
        // Where the threads on several lines that are placed start, and end: only those cover lines.
        List<Tuple2<Integer, Integer>> spans = found.flatMap(placed -> placed._2()
                .flatMap(last -> placed._1()
                        .startLine()
                        .map(start -> Tuple.of(
                                at(lines, Option.of(start), placed._1().startSide())
                                        .getOrElse(() -> firstAfter(
                                                file, starts, start, placed._1().startSide(), last)),
                                last))));
        Vector<Boolean> covered = spans.isEmpty() ? Vector.empty() : covered(spans, lines.size());
        return new FileRemarks(
                threads.filter(thread -> thread.subject() == Subject.FILE),
                file.hunks()
                        .zip(starts)
                        .map(hunk -> List.range(
                                        hunk._2(), hunk._2() + hunk._1().lines().size())
                                .map(line -> remarks(
                                        after.get(line).getOrElse(List.empty()),
                                        !covered.isEmpty() && covered.get(line)))),
                found.filter(placed -> placed._2().isEmpty()).map(Tuple2::_1));
    }

    /**
     * Where among {@code lines}, the lines of a file in the order of the diff, the first line with the number
     * {@code line} on {@code side} is; none when the diff does not show it, or there is no line, as for an outdated
     * thread.
     */
    private static Option<Integer> at(Vector<DiffLine> lines, Option<Integer> line, Side side) {
        return line.map(number -> lines.indexWhere(shown -> number(shown, side).contains(number)))
                .filter(index -> index >= 0);
    }

    /**
     * Where in {@code file}, whose hunks start among its lines at {@code starts}, the first hunk after the line with
     * the number {@code number} on {@code side} starts, which the diff does not show, as with whitespace ignored, or
     * hunks cut elsewhere than GitHub's: a hunk shows every line of its range on either side, so each hunk is wholly
     * before or wholly after that line. The start of the hunk of {@code last} when none is found before it.
     */
    private static int firstAfter(FileDiff file, Vector<Integer> starts, int number, Side side, int last) {
        return file.hunks()
                .zip(starts)
                .find(hunk ->
                        hunk._1().lines().exists(line -> number(line, side).exists(shown -> shown > number)))
                .map(Tuple2::_2)
                .filter(first -> first <= last)
                .getOrElse(() -> starts.findLast(start -> start <= last).getOrElse(0));
    }

    /**
     * For each of {@code count} lines, whether one of {@code spans} covers it, from its first line to before its last,
     * in the order of the diff: a removed line among added ones too, as GitHub highlights it. Each span opens where it
     * starts and closes where it ends, so a line is covered while more are open than closed.
     */
    private static Vector<Boolean> covered(List<Tuple2<Integer, Integer>> spans, int count) {
        Map<Integer, Integer> edges = spans.filter(span -> span._1() < span._2())
                .flatMap(span -> List.of(Tuple.of(span._1(), 1), Tuple.of(span._2(), -1)))
                .groupBy(Tuple2::_1)
                .mapValues(at -> at.map(Tuple2::_2).sum().intValue());
        return Vector.range(0, count)
                .scanLeft(0, (open, line) -> open + edges.get(line).getOrElse(0))
                .tail()
                .map(open -> open > 0);
    }

    private static LineRemarks remarks(List<ReviewThread> threads, boolean covered) {
        return threads.isEmpty() && !covered ? NONE : new LineRemarks(threads, covered);
    }

    private static Option<Integer> number(DiffLine line, Side side) {
        return switch (side) {
            case LEFT -> line.oldNumber();
            case RIGHT -> line.newNumber();
        };
    }

    /**
     * The threads on one file.
     *
     * @param onFile   the threads about the whole file, which go under its bar
     * @param lines    for each hunk, for each of its lines, the threads after it
     * @param unplaced the threads that cannot be placed, which go at the end of the file
     */
    public record FileRemarks(List<ReviewThread> onFile, List<List<LineRemarks>> lines, List<ReviewThread> unplaced) {

        /**
         * Whether no thread is on the file.
         */
        public boolean isEmpty() {
            return onFile.isEmpty()
                    && unplaced.isEmpty()
                    && lines.forAll(hunk -> hunk.forAll(line -> line.threads().isEmpty()));
        }
    }

    /**
     * The threads on a line.
     *
     * @param threads the threads that end on it, which go after it
     * @param covered whether a thread on several lines that is placed covers it, before its last
     */
    public record LineRemarks(List<ReviewThread> threads, boolean covered) {}
}
