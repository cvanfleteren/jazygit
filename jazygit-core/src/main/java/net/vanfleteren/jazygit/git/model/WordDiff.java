package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;

/**
 * Which words of a changed line changed. In each block of removed lines followed by added lines, the first removed
 * line is paired with the first added one, and so on; a pair is compared word by word. What git notes in a block,
 * like {@code \ No newline at end of file}, does not end it.
 */
public final class WordDiff {

    /**
     * How many cells the comparisons of a whole diff may fill: each pair fills the product of the counts of its words.
     * The comparisons run where the diff is drawn, so a diff of many long lines must not hold that up; once this is
     * spent, the lines left are drawn whole.
     */
    public static final long BUDGET = 2_000_000;

    // A word, a run of whitespace, or any other single character.
    private static final Pattern TOKEN = Pattern.compile("\\w+|\\s+|.", Pattern.UNICODE_CHARACTER_CLASS);
    // Below this share of the characters of a pair kept, the line was rewritten, and nothing is highlighted.
    private static final double MIN_KEPT = 0.5;
    // Longer pairs are not compared, whatever is left of the budget.
    private static final int MAX_CELLS = 250_000;

    private WordDiff() {}

    /**
     * The segments of each line of each of {@code hunks}, the hunks of a diff, which share one {@link #BUDGET}: one
     * unchanged segment for a line that is not part of a pair, or whose pair changed too much or was not compared;
     * else the parts it shares with its pair, and those it does not.
     */
    public static List<List<List<Segment>>> segments(List<List<DiffLine>> hunks) {
        return segments(hunks, BUDGET);
    }

    /**
     * Like {@link #segments(List)}, with {@code budget} cells to fill.
     */
    static List<List<List<Segment>>> segments(List<List<DiffLine>> hunks, long budget) {
        return hunks.foldLeft(
                        Tuple.of(List.<List<List<Segment>>>empty(), budget),
                        (done, hunk) -> hunk(hunk.toVector(), done._2).map1(done._1::prepend))
                ._1
                .reverse();
    }

    /**
     * The segments of the lines of a hunk, with {@code budget} cells to fill, and what is left of it.
     */
    private static Tuple2<List<List<Segment>>, Long> hunk(Vector<DiffLine> lines, long budget) {
        // Unfolded rather than recursive, as a hunk can be longer than the stack is deep.
        List<Block> blocks = List.unfoldRight(
                Tuple.of(0, budget),
                at -> Option.when(at._1 < lines.size(), () -> {
                    Block block = block(lines, at._1, at._2);
                    return Tuple.of(block, Tuple.of(block.next(), block.left()));
                }));
        return Tuple.of(
                blocks.flatMap(Block::segments),
                blocks.lastOption().map(Block::left).getOrElse(budget));
    }

    /**
     * The block that starts at line {@code at}: removed lines and the added lines after them, or else the one line
     * there, with {@code budget} cells to fill.
     */
    private static Block block(Vector<DiffLine> lines, int at, long budget) {
        if (lines.get(at).kind() != DiffLine.Kind.REMOVED) {
            return new Block(List.of(whole(lines.get(at).text())), at + 1, budget);
        }
        Vector<DiffLine> removedRun = run(lines, at, DiffLine.Kind.REMOVED);
        Vector<DiffLine> addedRun = run(lines, at + removedRun.size(), DiffLine.Kind.ADDED);
        Vector<DiffLine> removed = removedRun.filter(line -> line.kind() == DiffLine.Kind.REMOVED);
        Vector<DiffLine> added = addedRun.filter(line -> line.kind() == DiffLine.Kind.ADDED);
        Tuple2<List<Tuple2<List<Segment>, List<Segment>>>, Long> pairs = removed.zip(added)
                .foldLeft(Tuple.of(List.<Tuple2<List<Segment>, List<Segment>>>empty(), budget), (done, pair) -> {
                    Tuple2<Tuple2<List<Segment>, List<Segment>>, Long> compared =
                            compared(pair._1.text(), pair._2.text(), done._2);
                    return Tuple.of(done._1.prepend(compared._1), compared._2);
                })
                .map1(List::reverse);
        List<List<Segment>> removedSegments =
                pairs._1.map(Tuple2::_1).appendAll(removed.drop(pairs._1.size()).map(line -> whole(line.text())));
        List<List<Segment>> addedSegments =
                pairs._1.map(Tuple2::_2).appendAll(added.drop(pairs._1.size()).map(line -> whole(line.text())));
        return new Block(
                placed(removedRun, DiffLine.Kind.REMOVED, removedSegments)
                        .appendAll(placed(addedRun, DiffLine.Kind.ADDED, addedSegments)),
                at + removedRun.size() + addedRun.size(),
                pairs._2);
    }

    /**
     * The lines from {@code at} on that are of {@code kind}, with the notes among them.
     */
    private static Vector<DiffLine> run(Vector<DiffLine> lines, int at, DiffLine.Kind kind) {
        return lines.drop(at).takeWhile(line -> line.kind() == kind || line.kind() == DiffLine.Kind.NOTE);
    }

    /**
     * The segments of the lines of {@code run}: those of {@code kind} take {@code segments} in order, the notes are
     * whole.
     */
    private static List<List<Segment>> placed(Vector<DiffLine> run, DiffLine.Kind kind, List<List<Segment>> segments) {
        return run.foldLeft(
                        Tuple.of(List.<List<Segment>>empty(), segments),
                        (done, line) -> line.kind() == kind
                                ? Tuple.of(done._1.prepend(done._2.head()), done._2.tail())
                                : Tuple.of(done._1.prepend(whole(line.text())), done._2))
                ._1
                .reverse();
    }

    /**
     * The segments of {@code removed} and of {@code added}, each compared with the other.
     */
    static Tuple2<List<Segment>, List<Segment>> compared(String removed, String added) {
        return compared(removed, added, BUDGET)._1;
    }

    /**
     * Like {@link #compared(String, String)}, with {@code budget} cells to fill, and what is left of it. A pair that
     * would fill more is not compared.
     */
    private static Tuple2<Tuple2<List<Segment>, List<Segment>>, Long> compared(
            String removed, String added, long budget) {
        String[] before = tokens(removed);
        String[] after = tokens(added);
        long cells = (long) before.length * after.length;
        if (cells > MAX_CELLS || cells > budget) {
            return Tuple.of(Tuple.of(whole(removed), whole(added)), budget);
        }
        Tuple2<boolean[], boolean[]> kept = kept(before, after);
        int keptChars = Vector.range(0, before.length)
                .filter(i -> kept._1[i])
                .map(i -> before[i].length())
                .sum()
                .intValue();
        int total = removed.length() + added.length();
        return Tuple.of(
                total == 0 || 2.0 * keptChars / total < MIN_KEPT
                        ? Tuple.of(whole(removed), whole(added))
                        : Tuple.of(joined(before, kept._1), joined(after, kept._2)),
                budget - cells);
    }

    /**
     * Which of the tokens of {@code before} and of {@code after} are in their longest common subsequence.
     */
    private static Tuple2<boolean[], boolean[]> kept(String[] before, String[] after) {
        int n = before.length;
        int m = after.length;
        // The classic table, filled in place, on arrays as it is the hot loop: lengths[i][j] is the longest common
        // subsequence of the tokens from i and from j on.
        int[][] lengths = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lengths[i][j] = before[i].equals(after[j])
                        ? lengths[i + 1][j + 1] + 1
                        : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            }
        }
        boolean[] keptBefore = new boolean[n];
        boolean[] keptAfter = new boolean[m];
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (before[i].equals(after[j])) {
                keptBefore[i++] = true;
                keptAfter[j++] = true;
            } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return Tuple.of(keptBefore, keptAfter);
    }

    private static String[] tokens(String line) {
        return TOKEN.matcher(line).results().map(MatchResult::group).toArray(String[]::new);
    }

    /**
     * The {@code tokens}, the ones not {@code kept} changed, with neighbours that are alike joined.
     */
    private static List<Segment> joined(String[] tokens, boolean[] kept) {
        return Vector.range(0, tokens.length)
                .foldLeft(
                        List.<Segment>empty(),
                        (joined, i) -> joined.headOption()
                                .filter(last -> last.changed() == !kept[i])
                                .map(last ->
                                        joined.tail().prepend(new Segment(last.text() + tokens[i], last.changed())))
                                .getOrElse(() -> joined.prepend(new Segment(tokens[i], !kept[i]))))
                .reverse();
    }

    private static List<Segment> whole(String text) {
        return List.of(new Segment(text, false));
    }

    /**
     * A part of a line.
     *
     * @param changed whether it is not in the line it was compared with
     */
    public record Segment(String text, boolean changed) {}

    /**
     * The segments of the lines of a block, the line after it, and what is left of the budget.
     */
    private record Block(List<List<Segment>> segments, int next, long left) {}
}
