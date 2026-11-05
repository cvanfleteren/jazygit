package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.IntPredicate;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;

/**
 * The order the rich view shows the lines of a hunk in (ADR 83): a block of removed lines followed by as many added
 * ones, each pair of which {@link WordDiff} compared and found words in that changed, is shown a pair at a time, the
 * first removed line and the first added one, then the second of each, and so on. Any other block is shown as git
 * gives it: its removed lines, then its added ones. Blocks are cut as {@link WordDiff} cuts them, so a note among its
 * lines, like {@code \ No newline at end of file}, does not end one.
 */
public final class LinePairs {

    private LinePairs() {}

    /**
     * The lines of a hunk, {@code lines}, cut into the groups the rich view shows together, in the order it shows
     * them: each a pair, then the notes after either of its lines, or else one line by itself. What the rich view
     * puts after a line, like a review thread, goes after its group.
     *
     * @return for each group, the indexes of its lines among {@code lines}
     */
    public static List<List<Integer>> groups(List<ParsedLine> lines) {
        return groups(lines, index -> false);
    }

    /**
     * Like {@link #groups(List)}, but a block with a line whose index {@code kept} holds is shown as git gives it.
     */
    public static List<List<Integer>> groups(List<ParsedLine> lines, IntPredicate kept) {
        Vector<DiffLine> diffLines = lines.map(ParsedLine::line).toVector();
        Vector<Boolean> changed = lines.map(line -> line.words().exists(WordDiff.Segment::changed))
                .toVector();
        // Unfolded rather than recursive, as a hunk can be longer than the stack is deep.
        return List.unfoldRight(
                        0,
                        at -> Option.when(at < diffLines.size(), () -> {
                            Vector<Integer> removedRun = run(diffLines, at, DiffLine.Kind.REMOVED);
                            Vector<Integer> addedRun = run(diffLines, at + removedRun.size(), DiffLine.Kind.ADDED);
                            Vector<Integer> block = removedRun.appendAll(addedRun);
                            Vector<Integer> all = block.isEmpty() ? Vector.of(at) : block;
                            return Tuple.of(
                                    paired(diffLines, changed, removedRun, addedRun, kept)
                                            .getOrElse(() -> all.toList().map(List::of)),
                                    at + all.size());
                        }))
                .flatMap(groups -> groups);
    }

    /**
     * The indexes of the lines from {@code at} on that are of {@code kind}, with the notes among and after them; none
     * unless the line at {@code at} is of {@code kind}.
     */
    private static Vector<Integer> run(Vector<DiffLine> lines, int at, DiffLine.Kind kind) {
        if (at >= lines.size() || lines.get(at).kind() != kind) {
            return Vector.empty();
        }
        return Vector.range(at, lines.size())
                .takeWhile(index ->
                        lines.get(index).kind() == kind || lines.get(index).kind() == DiffLine.Kind.NOTE);
    }

    /**
     * The groups of a block, its {@code removedRun} and its {@code addedRun}, shown a pair at a time, if it is: when
     * it has as many removed lines as added ones, each pair has a changed word, and {@code kept} holds for none of
     * its lines.
     */
    private static Option<List<List<Integer>>> paired(
            Vector<DiffLine> lines,
            Vector<Boolean> changed,
            Vector<Integer> removedRun,
            Vector<Integer> addedRun,
            IntPredicate kept) {
        Vector<Integer> removed = removedRun.filter(index -> lines.get(index).kind() == DiffLine.Kind.REMOVED);
        Vector<Integer> added = addedRun.filter(index -> lines.get(index).kind() == DiffLine.Kind.ADDED);
        boolean pairs = !removed.isEmpty()
                && removed.size() == added.size()
                && removed.zip(added).forAll(pair -> changed.get(pair._1()) || changed.get(pair._2()))
                && !removedRun.appendAll(addedRun).exists(kept::test);
        return Option.when(
                pairs,
                () -> removed.zip(added)
                        .map(pair -> List.of(pair._1(), pair._2())
                                .appendAll(notesAfter(lines, removedRun, pair._1()))
                                .appendAll(notesAfter(lines, addedRun, pair._2())))
                        .toList());
    }

    /**
     * The indexes of the notes right after the line at {@code index} in {@code run}.
     */
    private static Vector<Integer> notesAfter(Vector<DiffLine> lines, Vector<Integer> run, int index) {
        return run.dropWhile(at -> at <= index).takeWhile(at -> lines.get(at).kind() == DiffLine.Kind.NOTE);
    }
}
