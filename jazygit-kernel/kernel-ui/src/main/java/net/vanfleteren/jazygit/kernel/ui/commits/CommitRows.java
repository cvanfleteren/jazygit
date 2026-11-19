package net.vanfleteren.jazygit.kernel.ui.commits;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.CommitGraph;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The rows of commits, as the commits pane, the commits of a branch, the divergence and the blame draw them: a
 * commit's short sha, the initials of its author, where it is in the graph, its tags and its message.
 */
public final class CommitRows {

    private static final int INITIALS_WIDTH = 2;
    private static final Pattern LETTER = Pattern.compile("\\X");

    private CommitRows() {}

    /**
     * The items of {@code commits}, a log, on the rails of its graph, with the commits on the base dimmed and the
     * fork point marked. Each row, and the graph down to it, is only worked out once the list draws it: a log of
     * thousands of commits then lays out only what is scrolled to. A row is one line.
     *
     * @param copied the commit copied to cherry-pick, whose sha stands out
     */
    public static List<LoadableList.Item> graphed(List<Commit> commits, Option<Sha> copied, AheadView ahead) {
        Stream<CommitGraph.Node> nodes = CommitGraph.laidOut(commits);
        return commits.zipWithIndex((commit, at) -> LoadableList.Item.later(
                1, () -> marked(row(nodes.get(at), copied.contains(commit.sha())), commit.sha(), ahead)));
    }

    /**
     * The row of a commit, a {@code ○}, or a {@code ◎} for a merge, on its rail. A merge opens a rail for the
     * branch it brought in with {@code ◎─╮}; the rail goes on as {@code │} until the commit the branch forked from,
     * where it joins with {@code ○─╯}, and further rails joining there with {@code ─┴} on the way. A merge that
     * opens a rail on one that joins it, or that goes on, draws {@code ┤}, and one it crosses {@code ┼}.
     */
    public static LoadableList.Row row(CommitGraph.Node node) {
        return row(node, false);
    }

    /**
     * @param copied whether the commit is copied to cherry-pick, which shows in its sha
     */
    public static LoadableList.Row row(CommitGraph.Node node, boolean copied) {
        return row(node.commit(), graph(node), copied);
    }

    /**
     * The row of {@code commit}.
     *
     * @param graph  where the commit is in the graph, before its message
     * @param copied whether the commit is copied to cherry-pick, which shows in its sha
     */
    public static LoadableList.Row row(Commit commit, String graph, boolean copied) {
        String author = " " + initials(commit.authorName()) + " " + graph;
        List<LoadableList.Seg> prefix = copied
                ? List.of(LoadableList.Seg.styled(commit.shortSha(), Theme.Classes.COPIED), LoadableList.Seg.of(author))
                : List.of(LoadableList.Seg.of(commit.shortSha() + author));
        List<LoadableList.Seg> tags =
                commit.tags().map(tag -> LoadableList.Seg.styled("[" + tag.name() + "] ", Theme.Classes.TAG));
        return new LoadableList.Row(prefix.appendAll(tags).append(LoadableList.Seg.of(commit.message())));
    }

    /**
     * {@code row}, that of the commit {@code sha}, dimmed when the commit is on the base.
     */
    public static LoadableList.Row marked(LoadableList.Row row, Sha sha, AheadView ahead) {
        return ahead.dimmed(sha)
                ? new LoadableList.Row(
                        row.segs().map(seg -> LoadableList.Seg.styled(seg.text(), Theme.Classes.DIM)), row.detail())
                : row;
    }

    /**
     * The rails of the row of {@code node}, each followed by a {@code ─} where the line from the commit to the
     * rails it joins and opens goes on, else a space.
     */
    private static String graph(CommitGraph.Node node) {
        List<Integer> line = node.joins().appendAll(node.opens()).append(node.column());
        int reach = line.max().get();
        int width = line.appendAll(node.passing()).max().get() + 1;
        // Which columns have a rail going up and which one going down, looked up once for the row rather than in
        // the rails for each column: a graph can be hundreds of rails wide.
        boolean[] up = columns(width, node.passing().appendAll(node.joins()));
        boolean[] down = columns(width, node.passing().appendAll(node.opens()));
        return IntStream.range(0, width)
                .mapToObj(
                        at -> rail(node, reach, at, up[at], down[at]) + (at >= node.column() && at < reach ? "─" : " "))
                .collect(Collectors.joining());
    }

    /**
     * For each of the {@code width} columns, whether it is one of {@code rails}.
     */
    private static boolean[] columns(int width, List<Integer> rails) {
        boolean[] on = new boolean[width];
        for (int at : rails) {
            on[at] = true;
        }
        return on;
    }

    /**
     * The rail in column {@code at} of the row of {@code node}, whose line reaches to the column {@code reach}.
     *
     * @param up   whether a rail comes in from above in the column
     * @param down whether a rail goes on below in the column
     */
    private static String rail(CommitGraph.Node node, int reach, int at, boolean up, boolean down) {
        if (at == node.column()) {
            return node.commit().isMerge() ? "◎" : "○";
        }
        if (at < node.column() || at > reach) {
            return up ? "│" : " ";
        }
        boolean on = at < reach;
        return up && down ? (on ? "┼" : "┤") : up ? (on ? "┴" : "╯") : down ? (on ? "┬" : "╮") : "─";
    }

    /**
     * The capitalized first letters of the first two words of {@code name}; a name of a single word gives
     * its first two letters as they are. No more than two columns: a CJK or emoji letter takes both.
     */
    public static String initials(String name) {
        List<String> words = List.of(name.strip().split("\\s+")).filter(word -> !word.isEmpty());
        if (words.size() < 2) {
            return TextWidth.start(name.strip(), INITIALS_WIDTH);
        }
        return TextWidth.start(
                words.take(2).map(word -> firstLetter(word).toUpperCase()).mkString(), INITIALS_WIDTH);
    }

    /**
     * The first letter of {@code word} as it is seen: a whole emoji or flag, with the marks on it.
     */
    private static String firstLetter(String word) {
        Matcher letter = LETTER.matcher(word);
        return letter.lookingAt() ? letter.group() : "";
    }
}
