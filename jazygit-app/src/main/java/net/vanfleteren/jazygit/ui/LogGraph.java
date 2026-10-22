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
     * last commit of a merged branch closes it with a {@code "/"} in place of its second line.
     */
    static Gutter gutter(CommitGraph.Node node) {
        String rails = "| ".repeat(node.depth());
        String closing = "| ".repeat(Math.max(0, node.depth() - 1));
        if (node.commit().isMerge()) {
            return new Gutter(rails + "*   ", Option.of(rails + "|\\  "), rails + "| | ", rails + "| | ");
        }
        if (node.closesBranch()) {
            return new Gutter(rails + "* ", Option.none(), closing + "|/  ", closing + "|   ");
        }
        return new Gutter(rails + "* ", Option.none(), rails + "| ", rails + "| ");
    }

    /**
     * What goes before each line of a commit in the graph.
     *
     * @param first   before its first line
     * @param opening before the line a merge adds after its first line to open the rail of its branch
     * @param second  before its second line
     * @param rest    before each line after that
     */
    record Gutter(String first, Option<String> opening, String second, String rest) {

        /**
         * The {@code lines} with the rails before them, and for a merge the line {@code openingText} after the
         * first one; no lines stay none.
         */
        List<String> draw(List<String> lines, String openingText) {
            List<String> drawn = lines.zipWith(
                    Stream.of(first, second).appendAll(Stream.continually(rest)), (line, rail) -> rail + line);
            return opening.filter(rail -> drawn.nonEmpty())
                    .fold(() -> drawn, rail -> drawn.insert(1, rail + openingText));
        }
    }
}
