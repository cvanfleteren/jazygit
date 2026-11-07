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
     * The rails before the lines of the commit of {@code node}, two characters a rail: each rail that joins the
     * commit first ends on a line of its own with a {@code "/"}, the outermost first, along {@code "_"} past the
     * rails in between; a merge opens the rails of its other parents on the line after its first with a
     * {@code "\"}, along {@code "-"} past the rails in between.
     */
    static Gutter gutter(CommitGraph.Node node) {
        int column = node.column();
        List<Integer> through = node.passing().append(column);
        List<Integer> below = through.appendAll(node.opens());
        int width = below.max().get() + 1;
        List<String> joining = node.joins()
                .reverse()
                .zipWithIndex()
                .map(join -> joining(node, join._1, node.joins().dropRight(join._2 + 1)));
        String first = line(width, at -> at % 2 == 1 ? " " : at / 2 == column ? "*" : rail(through, at));
        Option<String> opening = Option.when(node.opens().nonEmpty(), () -> opening(node, width));
        return new Gutter(joining, first, opening, line(width, at -> rail(below, at)));
    }

    /**
     * The line on which the rail {@code join} ends, while the rails in {@code joins} that end after it still go on.
     */
    private static String joining(CommitGraph.Node node, int join, List<Integer> joins) {
        List<Integer> rails = node.passing().append(node.column()).appendAll(joins);
        int into = joins.append(node.column()).max().get();
        return line(
                        Math.max(join, rails.max().get()) + 1,
                        at -> at == 2 * join - 1
                                ? "/"
                                : rails.contains(at / 2) && at % 2 == 0
                                        ? "|"
                                        : at > 2 * into && at < 2 * join - 1 ? "_" : " ")
                .stripTrailing();
    }

    /**
     * The line on which a merge opens the rails of its other parents.
     */
    private static String opening(CommitGraph.Node node, int width) {
        List<Integer> rails = node.passing().append(node.column());
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
     * What goes before each line of a commit in the graph.
     *
     * @param joining the lines before the commit on which rails join it
     * @param first   before its first line
     * @param opening before the line a merge adds after its first line to open the rails of its other parents
     * @param rest    before each line after those
     */
    record Gutter(List<String> joining, String first, Option<String> opening, String rest) {

        /**
         * The {@code lines} with the rails before them, after the lines on which rails join, and for a merge the
         * line {@code openingText} after the first one; no lines stay none.
         */
        List<String> draw(List<String> lines, String openingText) {
            List<String> drawn =
                    lines.zipWith(Stream.of(first).appendAll(Stream.continually(rest)), (line, rail) -> rail + line);
            return drawn.isEmpty()
                    ? drawn
                    : opening.fold(() -> drawn, rail -> drawn.insert(1, rail + openingText))
                            .prependAll(joining);
        }
    }
}
