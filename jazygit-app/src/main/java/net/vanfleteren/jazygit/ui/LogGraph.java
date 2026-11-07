package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import java.util.function.IntFunction;
import net.vanfleteren.jazygit.git.model.CommitGraph;

/**
 * The rails of a commit graph as text, like {@code git log --graph} draws them.
 */
final class LogGraph {

    private LogGraph() {}

    /**
     * The gutters of the commits of {@code nodes}, in their order. Like git, a rail that comes to wait for the same
     * commit as a rail to its left ends right away, so it is no longer drawn where it would join that commit.
     */
    static List<Gutter> gutters(List<CommitGraph.Node> nodes) {
        return nodes.foldLeft(new Drawn(List.empty(), List.empty()), Drawn::next)
                .latestFirst()
                .reverse();
    }

    /**
     * The rails before the lines of the commit of {@code node}, two characters a rail, without the rails in
     * {@code ended}. A rail that joins the commit and did not end yet ends on a line of its own before it with a
     * {@code "/"}, the outermost first, along {@code "_"} past the rails in between. A merge opens the rails of its
     * other parents on the line after its first with a {@code "\"}, along {@code "-"} past the rails in between.
     * The rails that meet one to their left end after that, the same way, one a line.
     */
    static Gutter gutter(CommitGraph.Node node, List<Integer> ended) {
        int column = node.column();
        List<Integer> through = node.passing().removeAll(ended).append(column);
        List<Integer> below = through.appendAll(node.opens());
        int width = below.max().get() + 1;
        List<Integer> joins = node.joins().removeAll(ended);
        List<String> joining = joins.reverse()
                .zipWithIndex()
                .map(join -> ending(
                                Math.max(join._1, through.max().get()) + 1,
                                join._1,
                                joins.dropRight(join._2 + 1)
                                        .append(column)
                                        .max()
                                        .get(),
                                through.appendAll(joins.dropRight(join._2 + 1)))
                        .stripTrailing());
        List<CommitGraph.Meet> meets =
                node.meets().sortBy(CommitGraph.Meet::rail).reverse();
        List<String> meeting = meets.zipWithIndex()
                .map(meet -> ending(
                        width,
                        meet._1.rail(),
                        meet._1.into(),
                        below.removeAll(meets.take(meet._2 + 1).map(CommitGraph.Meet::rail))));
        String first = line(width, at -> at % 2 == 1 ? " " : at / 2 == column ? "*" : rail(through, at));
        Option<String> opening = Option.when(node.opens().nonEmpty(), () -> opening(node, through, width));
        String rest = line(width, at -> rail(below.removeAll(meets.map(CommitGraph.Meet::rail)), at));
        return new Gutter(joining, first, opening, meeting, rest);
    }

    /**
     * A line {@code width} rails wide on which the rail {@code end} ends in the rail {@code into}, while
     * {@code rails} go on.
     */
    private static String ending(int width, int end, int into, List<Integer> rails) {
        return line(
                width,
                at -> at == 2 * end - 1
                        ? "/"
                        : rails.contains(at / 2) && at % 2 == 0 ? "|" : at > 2 * into && at < 2 * end - 1 ? "_" : " ");
    }

    /**
     * The line on which a merge opens the rails of its other parents, while {@code rails} go on.
     */
    private static String opening(CommitGraph.Node node, List<Integer> rails, int width) {
        int reach = node.opens().max().get();
        return line(
                width,
                at -> node.opens().contains((at + 1) / 2) && at % 2 == 1
                        ? "\\"
                        : rails.contains(at / 2) && at % 2 == 0
                                ? "|"
                                : at > 2 * node.column() && at < 2 * reach - 1 ? "-" : " ");
    }

    private static String rail(List<Integer> rails, int at) {
        return at % 2 == 0 && rails.contains(at / 2) ? "|" : " ";
    }

    /**
     * The {@code width} rails of a line, two characters each: what {@code at} gives for each character.
     */
    private static String line(int width, IntFunction<String> at) {
        return List.range(0, 2 * width).map(at::apply).mkString();
    }

    /**
     * The rails ended so far, as they met one to their left, and the gutters so far, last first.
     */
    private record Drawn(List<Integer> ended, List<Gutter> latestFirst) {

        Drawn next(CommitGraph.Node node) {
            return new Drawn(
                    ended.removeAll(node.joins())
                            .removeAll(node.opens())
                            .appendAll(node.meets().map(CommitGraph.Meet::rail)),
                    latestFirst.prepend(gutter(node, ended)));
        }
    }

    /**
     * What goes before each line of a commit in the graph.
     *
     * @param joining the lines before the commit on which rails join it
     * @param first   before its first line
     * @param opening before the line a merge adds after its first line to open the rails of its other parents
     * @param next    before the lines after the first one, in order, until {@code rest} takes over
     * @param rest    before each line after those
     */
    record Gutter(List<String> joining, String first, Option<String> opening, List<String> next, String rest) {

        /**
         * The {@code lines} with the rails before them, after the lines on which rails join, and for a merge the
         * line {@code openingText} after the first one; no lines stay none. Blank lines are added when {@code next}
         * needs more lines than there are.
         */
        List<String> draw(List<String> lines, String openingText) {
            List<String> padded = lines.isEmpty()
                    ? lines
                    : lines.appendAll(List.fill(Math.max(0, 1 + next.size() - lines.size()), ""));
            List<String> drawn = padded.zipWith(
                    Stream.of(first).appendAll(next).appendAll(Stream.continually(rest)), (line, rail) -> rail + line);
            return drawn.isEmpty()
                    ? drawn
                    : opening.fold(() -> drawn, rail -> drawn.insert(1, rail + openingText))
                            .prependAll(joining);
        }
    }
}
