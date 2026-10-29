package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;

/**
 * Which words of a changed line changed. In each block of removed lines followed by added lines, the first removed
 * line is paired with the first added one, and so on; a pair is compared word by word.
 */
public final class WordDiff {

    // A word, a run of whitespace, or any other single character.
    private static final Pattern TOKEN = Pattern.compile("\\w+|\\s+|.", Pattern.UNICODE_CHARACTER_CLASS);
    // Below this share of the characters of a pair kept, the line was rewritten, and nothing is highlighted.
    private static final double MIN_KEPT = 0.5;
    // Longer pairs are not compared: the table of the comparison grows with the product of their lengths.
    private static final int MAX_CELLS = 250_000;

    private WordDiff() {}

    /**
     * The segments of each of {@code lines}, the lines of a hunk: one unchanged segment for a line that is not part
     * of a pair, or whose pair changed too much; else the parts it shares with its pair, and those it does not.
     */
    public static List<List<Segment>> segments(List<DiffLine> lines) {
        Vector<DiffLine> all = lines.toVector();
        // Unfolded rather than recursive, as a hunk can be longer than the stack is deep.
        return List.unfoldRight(0, at -> Option.when(at < all.size(), () -> block(all, at)))
                .flatMap(block -> block);
    }

    /**
     * The segments of the block that starts at line {@code at}: removed lines and the added lines after them, or
     * else the one line there; and where the next block starts.
     */
    private static Tuple2<List<List<Segment>>, Integer> block(Vector<DiffLine> lines, int at) {
        Vector<DiffLine> removed = lines.drop(at).takeWhile(line -> line.kind() == DiffLine.Kind.REMOVED);
        Vector<DiffLine> added = lines.drop(at + removed.size()).takeWhile(line -> line.kind() == DiffLine.Kind.ADDED);
        if (removed.isEmpty()) {
            return Tuple.of(List.of(whole(lines.get(at).text())), at + 1);
        }
        if (added.isEmpty()) {
            return Tuple.of(removed.map(line -> whole(line.text())).toList(), at + removed.size());
        }
        List<Tuple2<List<Segment>, List<Segment>>> pairs = removed.zip(added)
                .map(pair -> compared(pair._1.text(), pair._2.text()))
                .toList();
        List<List<Segment>> unpaired = List.ofAll(removed.drop(pairs.size()))
                .appendAll(added.drop(pairs.size()))
                .map(line -> whole(line.text()));
        return Tuple.of(
                pairs.map(Tuple2::_1)
                        .appendAll(unpaired.take(removed.size() - pairs.size()))
                        .appendAll(pairs.map(Tuple2::_2))
                        .appendAll(unpaired.drop(removed.size() - pairs.size())),
                at + removed.size() + added.size());
    }

    /**
     * The segments of {@code removed} and of {@code added}, each compared with the other.
     */
    static Tuple2<List<Segment>, List<Segment>> compared(String removed, String added) {
        Vector<String> before = tokens(removed);
        Vector<String> after = tokens(added);
        if ((long) before.size() * after.size() > MAX_CELLS) {
            return Tuple.of(whole(removed), whole(added));
        }
        Tuple2<Vector<Boolean>, Vector<Boolean>> kept = kept(before, after);
        int keptChars = before.zip(kept._1)
                .filter(Tuple2::_2)
                .map(token -> token._1.length())
                .sum()
                .intValue();
        int total = removed.length() + added.length();
        return total == 0 || 2.0 * keptChars / total < MIN_KEPT
                ? Tuple.of(whole(removed), whole(added))
                : Tuple.of(joined(before, kept._1), joined(after, kept._2));
    }

    /**
     * Which of the tokens of {@code before} and of {@code after} are in their longest common subsequence.
     */
    private static Tuple2<Vector<Boolean>, Vector<Boolean>> kept(Vector<String> before, Vector<String> after) {
        int n = before.size();
        int m = after.size();
        // The classic table, filled in place: lengths[i][j] is the longest common subsequence of the tokens from i
        // and from j on.
        int[][] lengths = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lengths[i][j] = before.get(i).equals(after.get(j))
                        ? lengths[i + 1][j + 1] + 1
                        : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            }
        }
        boolean[] keptBefore = new boolean[n];
        boolean[] keptAfter = new boolean[m];
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (before.get(i).equals(after.get(j))) {
                keptBefore[i++] = true;
                keptAfter[j++] = true;
            } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return Tuple.of(flags(keptBefore), flags(keptAfter));
    }

    private static Vector<Boolean> flags(boolean[] flags) {
        return Vector.range(0, flags.length).map(index -> flags[index]);
    }

    private static Vector<String> tokens(String line) {
        Matcher matcher = TOKEN.matcher(line);
        return Vector.ofAll(
                matcher.results().map(java.util.regex.MatchResult::group).toList());
    }

    /**
     * The {@code tokens}, the ones not {@code kept} changed, with neighbours that are alike joined.
     */
    private static List<Segment> joined(Vector<String> tokens, Vector<Boolean> kept) {
        return tokens.zip(kept)
                .foldLeft(
                        List.<Segment>empty(),
                        (joined, token) -> joined.headOption()
                                .filter(last -> last.changed() == !token._2)
                                .map(last -> joined.tail().prepend(new Segment(last.text() + token._1, last.changed())))
                                .getOrElse(() -> joined.prepend(new Segment(token._1, !token._2))))
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
}
