package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn.Mark;
import net.vanfleteren.jazygit.kernel.ui.text.Rows;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * How the review threads of a file are drawn: as blocks that start {@code indent} columns in, two after the gutter, and
 * are {@code width} columns wide, as {@code style} says.
 */
record Blocks(int indent, int width, RemarkLines.RemarkStyle style) {

    /**
     * The lines of {@code threads}, after {@code line} if they are, each a stop of the cursor, then a blank line that
     * sets them apart from what follows them in their file, if they are {@code followed} by anything.
     */
    List<Marked> threads(
            List<RemarkThread> threads, Option<Anchor.OnLine> line, boolean followed, Theme.SpanStyles styles) {
        return marked(threads, line, RemarkLines.blocks(threads, indent, width, style, styles))
                .appendAll(followed && !threads.isEmpty() ? List.of(Marked.plain(Line.from(""))) : List.empty());
    }

    /**
     * The lines of {@code threads}, which cannot be placed, under a line that says so, each a stop of the cursor.
     */
    List<Marked> unplaced(List<RemarkThread> threads, Theme.SpanStyles styles) {
        return threads.isEmpty()
                ? List.empty()
                : marked(threads, Option.none(), RemarkLines.unplacedBlocks(threads, indent, width, style, styles))
                        .prepend(Marked.plain(RemarkLines.unplacedHeading(indent, styles)));
    }

    /**
     * The {@code blocks} of {@code threads}, with a blank line between two of them, the first line of each of their
     * parts the start of its stop, as {@link DiffStops#anchors} gives them (ADR 62).
     */
    private List<Marked> marked(List<RemarkThread> threads, Option<Anchor.OnLine> line, List<List<List<Line>>> blocks) {
        return threads.zip(blocks)
                .map(block -> DiffStops.anchors(block._1(), line, style.collapsed())
                        .zip(block._2())
                        .flatMap(part -> part._2()
                                .zipWithIndex()
                                .<Marked>map(drawn -> Marked.of(
                                        Rows.kept(drawn._1()),
                                        Option.when(
                                                drawn._2() == 0,
                                                () -> new Mark(
                                                        part._1(), part._2().size(), indent + width, Option.none()))))))
                .intersperse(List.of(Marked.plain(Line.from(""))))
                .flatMap(Function.identity());
    }
}
