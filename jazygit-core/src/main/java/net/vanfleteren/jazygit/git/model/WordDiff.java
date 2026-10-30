package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;

/**
 * Which words of a changed line changed. In each block of removed lines followed by added lines, the first removed
 * line is paired with the first added one, and so on; a pair is compared word by word. What git notes in a block,
 * like {@code \ No newline at end of file}, does not end it.
 */
public final class WordDiff {

    /**
     * How many cells the comparisons of a whole diff may fill: each pair fills the product of the counts of its words,
     * leaving out those it starts and ends with alike; a pair that could not keep enough to be highlighted, whatever
     * the order of its words, fills none.
     * The comparisons run where the diff is drawn, so a diff of many long lines must not hold that up; once this is
     * spent, the lines left are drawn whole, but for a pair that differs in an insertion or a deletion only, which
     * fills no cells.
     */
    public static final long BUDGET = 2_000_000;

    // What a character of a line is part of: a word, a run of whitespace, or neither, when it is a token by itself.
    static final int WORD = 0;
    static final int SPACE = 1;
    static final int OTHER = 2;
    // The join controls, which Unicode counts as word characters, and the one whitespace below U+00A0 outside 9-13.
    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int NEXT_LINE = 0x85;
    // Below this share of the characters of a pair kept, the line was rewritten, and nothing is highlighted.
    private static final double MIN_KEPT = 0.5;
    // Longer pairs are not compared, whatever is left of the budget.
    private static final int MAX_CELLS = 250_000;

    private WordDiff() {}

    /**
     * The segments of each line of each of {@code hunks}, the hunks of a diff, which share one {@link #BUDGET}: one
     * unchanged segment for a line that is not part of a pair, or whose pair changed too much or was not compared as
     * the budget was spent; else the parts it shares with its pair, and those it does not.
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
        // The tokens both start and end with are kept as they are; only those between them are compared, which for a
        // line changed in a word or two leaves next to nothing.
        int prefix = commonPrefix(before, after);
        int suffix = commonSuffix(before, after, prefix);
        int total = removed.length() + added.length();
        long cells = (long) (before.length - prefix - suffix) * (after.length - prefix - suffix);
        // A pair that could not keep enough even if its tokens were in the same order was rewritten: it is drawn whole
        // without filling the table, and leaves the budget to the pairs after it. Counting its tokens is not worth it
        // for a table of no more cells than it has tokens to count.
        int between = before.length + after.length - 2 * (prefix + suffix);
        if (cells > MAX_CELLS
                || cells > budget
                || (cells > between && rewritten(mostKept(before, after, prefix, suffix), total))) {
            return Tuple.of(Tuple.of(whole(removed), whole(added)), budget);
        }
        Tuple2<boolean[], boolean[]> kept = kept(before, after, prefix, suffix);
        int keptChars = IntStream.range(0, before.length)
                .filter(i -> kept._1[i])
                .map(i -> before[i].length())
                .sum();
        return Tuple.of(
                rewritten(keptChars, total)
                        ? Tuple.of(whole(removed), whole(added))
                        : Tuple.of(joined(before, kept._1), joined(after, kept._2)),
                budget - cells);
    }

    /**
     * Whether a pair of {@code total} characters, of which {@code keptChars} of each line are kept, was rewritten.
     */
    private static boolean rewritten(int keptChars, int total) {
        return total == 0 || 2.0 * keptChars / total < MIN_KEPT;
    }

    /**
     * The most characters of {@code before} its longest common subsequence with {@code after} can keep: those of
     * the first {@code prefix} and the last {@code suffix} tokens, and of each token between them as often as both
     * have it there, whatever their order. Counting them takes no table.
     */
    private static int mostKept(String[] before, String[] after, int prefix, int suffix) {
        // Counted in a map filled in place, as it runs for most pairs that are compared.
        Map<String, int[]> inAfter = new HashMap<>();
        for (int j = prefix; j < after.length - suffix; j++) {
            inAfter.computeIfAbsent(after[j], token -> new int[1])[0]++;
        }
        int kept = IntStream.concat(IntStream.range(0, prefix), IntStream.range(before.length - suffix, before.length))
                .map(i -> before[i].length())
                .sum();
        for (int i = prefix; i < before.length - suffix; i++) {
            int[] left = inAfter.get(before[i]);
            if (left != null && left[0] > 0) {
                left[0]--;
                kept += before[i].length();
            }
        }
        return kept;
    }

    /**
     * How many tokens {@code before} and {@code after} start with alike.
     */
    private static int commonPrefix(String[] before, String[] after) {
        int shorter = Math.min(before.length, after.length);
        return IntStream.range(0, shorter)
                .filter(i -> !before[i].equals(after[i]))
                .findFirst()
                .orElse(shorter);
    }

    /**
     * How many tokens {@code before} and {@code after} end with alike, of those after the first {@code prefix}.
     */
    private static int commonSuffix(String[] before, String[] after, int prefix) {
        int shorter = Math.min(before.length, after.length) - prefix;
        return IntStream.range(0, shorter)
                .filter(i -> !before[before.length - 1 - i].equals(after[after.length - 1 - i]))
                .findFirst()
                .orElse(shorter);
    }

    /**
     * Which of the tokens of {@code before} and of {@code after} are in their longest common subsequence: the first
     * {@code prefix} and the last {@code suffix}, which they share, and those of the longest common subsequence of the
     * tokens between them.
     */
    private static Tuple2<boolean[], boolean[]> kept(String[] before, String[] after, int prefix, int suffix) {
        int n = before.length - suffix;
        int m = after.length - suffix;
        boolean[] keptBefore = new boolean[before.length];
        boolean[] keptAfter = new boolean[after.length];
        Arrays.fill(keptBefore, 0, prefix, true);
        Arrays.fill(keptAfter, 0, prefix, true);
        Arrays.fill(keptBefore, n, before.length, true);
        Arrays.fill(keptAfter, m, after.length, true);
        // The classic table, filled in place, on arrays as it is the hot loop: lengths[i][j] is the longest common
        // subsequence of the tokens from prefix + i and from prefix + j on, up to the suffix.
        int[][] lengths = new int[n - prefix + 1][m - prefix + 1];
        for (int i = n - prefix - 1; i >= 0; i--) {
            for (int j = m - prefix - 1; j >= 0; j--) {
                lengths[i][j] = before[prefix + i].equals(after[prefix + j])
                        ? lengths[i + 1][j + 1] + 1
                        : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (prefix + i < n && prefix + j < m) {
            if (before[prefix + i].equals(after[prefix + j])) {
                keptBefore[prefix + i++] = true;
                keptAfter[prefix + j++] = true;
            } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return Tuple.of(keptBefore, keptAfter);
    }

    /**
     * The words, runs of whitespace and single other characters of {@code line}, scanned by hand rather than matched
     * with a pattern, as every changed line is split.
     */
    static String[] tokens(String line) {
        java.util.List<String> tokens = new ArrayList<>();
        int start = 0;
        while (start < line.length()) {
            int kind = kind(line.codePointAt(start));
            int end = start + Character.charCount(line.codePointAt(start));
            while (kind != OTHER && end < line.length() && kind(line.codePointAt(end)) == kind) {
                end += Character.charCount(line.codePointAt(end));
            }
            tokens.add(line.substring(start, end));
            start = end;
        }
        return tokens.toArray(String[]::new);
    }

    /**
     * Whether {@code codePoint} is part of a word, of whitespace, or neither, as {@code \w} and {@code \s} have it
     * with {@link java.util.regex.Pattern#UNICODE_CHARACTER_CLASS}: a word is the Unicode Alphabetic property, a
     * digit, a mark, connector punctuation or a join control; whitespace is the Unicode White_Space property.
     */
    static int kind(int codePoint) {
        if (Character.isAlphabetic(codePoint)
                || Character.isDigit(codePoint)
                || codePoint == ZERO_WIDTH_NON_JOINER
                || codePoint == ZERO_WIDTH_JOINER) {
            return WORD;
        }
        if (Character.isSpaceChar(codePoint) || (codePoint >= '\t' && codePoint <= '\r') || codePoint == NEXT_LINE) {
            return SPACE;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.NON_SPACING_MARK,
                    Character.ENCLOSING_MARK,
                    Character.COMBINING_SPACING_MARK -> WORD;
            default -> OTHER;
        };
    }

    /**
     * The {@code tokens}, the ones not {@code kept} changed, with neighbours that are alike joined. Whitespace kept
     * between two changed tokens is changed too, so that changed words with only whitespace between them are one
     * segment rather than one each.
     */
    private static List<Segment> joined(String[] tokens, boolean[] kept) {
        // A run of whitespace is one token, so whitespace between two changed tokens is a single token.
        IntPredicate unchanged = i -> kept[i]
                && !(i > 0
                        && i < tokens.length - 1
                        && !kept[i - 1]
                        && !kept[i + 1]
                        && kind(tokens[i].codePointAt(0)) == SPACE);
        // Where each run of alike tokens starts; each run is joined at once, as joining it a token at a time copies
        // it once for every token.
        int[] starts = IntStream.rangeClosed(0, tokens.length)
                .filter(i -> i == 0 || i == tokens.length || unchanged.test(i) != unchanged.test(i - 1))
                .toArray();
        return List.range(0, starts.length - 1)
                .map(run -> Tuple.of(starts[run], starts[run + 1]))
                .map(run -> new Segment(
                        String.join("", Arrays.asList(tokens).subList(run._1, run._2)), !unchanged.test(run._1)));
    }

    private static List<Segment> whole(String text) {
        return List.of(new Segment(text, false));
    }

    /**
     * A part of a line.
     *
     * @param changed whether it is not in the line it was compared with, or is whitespace between two parts that are
     *     not
     */
    public record Segment(String text, boolean changed) {}

    /**
     * The segments of the lines of a block, the line after it, and what is left of the budget.
     */
    private record Block(List<List<Segment>> segments, int next, long left) {}
}
