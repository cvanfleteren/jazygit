package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;

/**
 * The review threads of a pull request placed in a parsed diff of some of the files it changes (ADR 55): each after
 * the line it is on, about its whole file, or in a list at the end of its file when it cannot be placed.
 *
 * @param files for each file of the diff, in its order, the threads on it
 */
public record PlacedRemarks(List<FileRemarks> files) {

    /**
     * {@code threads} placed in {@code diff}. A thread goes after the line with its number on its side, in the file
     * with its path: the number in the new file for {@link Side#RIGHT}, in the old one for {@link Side#LEFT}; one on
     * several lines after its last one, covering every line of the diff from its first, on its own side, to it. One
     * about a whole file goes under its bar. One
     * that cannot be placed, outdated or on a line the diff does not show, is listed at the end of its file. A thread
     * of a file the diff does not have is left out: it is shown with the diff of its own file.
     */
    public static PlacedRemarks place(UnifiedDiff diff, List<ReviewThread> threads) {
        return new PlacedRemarks(diff.files().map(file -> file(file, threads.filter(thread -> of(file, thread)))));
    }

    /**
     * Whether no thread is placed anywhere.
     */
    public boolean isEmpty() {
        return files.forAll(FileRemarks::isEmpty);
    }

    private static boolean of(FileDiff file, ReviewThread thread) {
        return file.path().equals(thread.path()) || file.origin().contains(thread.path());
    }

    private static FileRemarks file(FileDiff file, List<ReviewThread> threads) {
        List<ReviewThread> onLines = threads.filter(thread -> thread.subject() == Subject.LINE);
        List<Tuple2<ReviewThread, Option<Tuple2<Integer, Integer>>>> found =
                onLines.map(thread -> Tuple.of(thread, at(file, thread.line(), thread.side())));
        // Where the threads on several lines that are placed start, and end: only those cover lines.
        List<Tuple2<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>>> spans = found.flatMap(placed -> placed._2()
                .flatMap(last -> at(file, placed._1().startLine(), placed._1().startSide())
                        .map(first -> Tuple.of(first, last))));
        return new FileRemarks(
                threads.filter(thread -> thread.subject() == Subject.FILE),
                file.hunks()
                        .zipWithIndex()
                        .map(hunk -> hunk._1()
                                .lines()
                                .zipWithIndex()
                                .map(line -> new LineRemarks(
                                        found.filter(placed -> placed._2().contains(Tuple.of(hunk._2(), line._2())))
                                                .map(Tuple2::_1),
                                        covered(spans, Tuple.of(hunk._2(), line._2()))))),
                found.filter(placed -> placed._2().isEmpty()).map(Tuple2::_1));
    }

    /**
     * Where in {@code file} the line with the number {@code line} on {@code side} is: the index of its hunk, and of the
     * line in it; none when the diff does not show it, or there is no line, as for an outdated thread.
     */
    private static Option<Tuple2<Integer, Integer>> at(FileDiff file, Option<Integer> line, Side side) {
        return line.flatMap(number -> file.hunks()
                .zipWithIndex()
                .flatMap(hunk -> hunk._1()
                        .lines()
                        .zipWithIndex()
                        .filter(shown -> number(shown._1(), side).contains(number))
                        .map(shown -> Tuple.of(hunk._2(), shown._2())))
                .headOption());
    }

    /**
     * Whether the line at {@code position} is covered by one of {@code spans}, from its first line to before its last,
     * in the order of the diff: a removed line among added ones too, as GitHub highlights it.
     */
    private static boolean covered(
            List<Tuple2<Tuple2<Integer, Integer>, Tuple2<Integer, Integer>>> spans, Tuple2<Integer, Integer> position) {
        return spans.exists(span -> span._1().compareTo(position) <= 0 && position.compareTo(span._2()) < 0);
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
