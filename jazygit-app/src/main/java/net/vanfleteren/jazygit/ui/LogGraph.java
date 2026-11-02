package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitGraph;

/**
 * The rails of a commit graph as text, like {@code git log --graph} draws them.
 */
final class LogGraph {

    private LogGraph() {}

    /**
     * The rails before the lines of the commit of {@code node}: a merge opens a rail with a {@code "\"} line, the
     * last commit of merged branches closes each of them with a {@code "/"}, one line further out per branch, in
     * place of its next lines.
     */
    static Gutter gutter(CommitGraph.Node node) {
        String rails = "| ".repeat(node.depth());
        if (node.commit().isMerge()) {
            return new Gutter(rails + "*   ", Option.of(rails + "|\\  "), List.empty(), rails + "| | ");
        }
        if (node.closes() > 0) {
            List<String> closing = List.rangeClosed(1, node.closes())
                    .map(closed -> "| ".repeat(node.depth() - closed) + "|/" + "  ".repeat(closed));
            return new Gutter(
                    rails + "* ",
                    Option.none(),
                    closing,
                    "| ".repeat(node.depth() - node.closes()) + "|" + " ".repeat(2 * node.closes() + 1));
        }
        return new Gutter(rails + "* ", Option.none(), List.empty(), rails + "| ");
    }

    /**
     * What goes before each line of a commit in the graph.
     *
     * @param first   before its first line
     * @param opening before the line a merge adds after its first line to open the rail of its branch
     * @param next    before the lines after the first one, in order, until {@code rest} takes over
     * @param rest    before each line after those
     */
    record Gutter(String first, Option<String> opening, List<String> next, String rest) {

        /**
         * The {@code lines} with the rails before them, and for a merge the line {@code openingText} after the
         * first one; no lines stay none. Blank lines are added when {@code next} needs more lines than there are.
         */
        List<String> draw(List<String> lines, String openingText) {
            List<String> padded = lines.isEmpty()
                    ? lines
                    : lines.appendAll(List.fill(Math.max(0, 1 + next.size() - lines.size()), ""));
            List<String> drawn = padded.zipWith(
                    Stream.of(first).appendAll(next).appendAll(Stream.continually(rest)), (line, rail) -> rail + line);
            return opening.filter(rail -> drawn.nonEmpty())
                    .fold(() -> drawn, rail -> drawn.insert(1, rail + openingText));
        }
    }
}
