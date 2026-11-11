package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Style;
import dev.tamboui.style.StylePropertyResolver;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.syntax.SyntaxHighlighter;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Objects;
import java.util.function.Function;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.ui.widgets.MarkdownImages;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * Markdown drawn as lines of spans, as tamboui draws it into a buffer off the screen: the text of a review remark
 * (ADR 55), and a version of a Markdown file rendered (ADR 103).
 */
final class MarkdownLines {

    private MarkdownLines() {}

    /**
     * {@code markdown} drawn {@code width} columns wide, as tamboui draws it into a buffer off the screen, its cells
     * read back as lines of spans: each run of cells of one style a span, without the blank cells at the end of a
     * line. It is as high as tamboui measures it; empty text has no lines. tamboui cuts off the lines of a code block
     * at its border, so a code block too wide for it is drawn on its own, as wide as its lines, which the diff scrolls
     * to, with the text around it still {@code width} wide and a blank line between them.
     */
    static List<Line> markdown(String markdown, int width, StylePropertyResolver resolver) {
        return markdown(markdown, width, resolver, List.empty());
    }

    /**
     * {@code markdown} drawn as {@link #markdown(String, int, StylePropertyResolver)} draws it, with each cell of a
     * character {@link MarkdownImages#PICTURE} the next cell of the first of {@code pictures}, of the character after
     * it the next of the second, and so on (ADR 84).
     */
    static List<Line> markdown(String markdown, int width, StylePropertyResolver resolver, List<Picture> pictures) {
        return markdown(markdown, width, resolver, pictures, Option.none());
    }

    /**
     * {@code markdown} drawn as {@link #markdown(String, int, StylePropertyResolver, List)} draws it, its code blocks
     * coloured by {@code highlighter} if one is given, else by tamboui's own.
     */
    static List<Line> markdown(
            String markdown,
            int width,
            StylePropertyResolver resolver,
            List<Picture> pictures,
            Option<SyntaxHighlighter> highlighter) {
        if (markdown.isBlank()) {
            return List.empty();
        }
        List<Piece> pieces = pieces(markdown, width);
        return pieces.forAll(piece -> piece.width() == width)
                ? drawn(markdown, width, resolver, pictures, highlighter)
                : pieces.map(piece -> drawn(piece.markdown(), piece.width(), resolver, pictures, highlighter))
                        // A piece of only link definitions draws nothing, and takes no blank line.
                        .filter(drawn -> !drawn.isEmpty())
                        .intersperse(List.of(Line.from("")))
                        .flatMap(Function.identity());
    }

    /**
     * {@code markdown} in pieces: each code block at its top too wide for {@code width} a piece of its own, as wide as
     * its widest line and its border, and the text between them pieces {@code width} wide. Each piece of text also
     * holds the link definitions of the whole, as a link in one piece may be defined in another.
     */
    private static List<Piece> pieces(String markdown, int width) {
        Node document = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                .build()
                .parse(markdown);
        Vector<String> lines = Vector.ofAll(markdown.lines());
        List<Tuple2<Node, Integer>> blocks = List.ofAll(Iterator.iterate(document.getFirstChild(), Node::getNext)
                        .takeWhile(Objects::nonNull))
                .filter(block -> !block.getSourceSpans().isEmpty())
                .map(block -> Tuple.of(block, wide(block, width)));
        // Each block joins the piece before it, unless one of the two is a wide code block.
        List<List<Tuple2<Node, Integer>>> runs = blocks.foldLeft(
                        List.<List<Tuple2<Node, Integer>>>empty(),
                        (done, block) -> done.headOption()
                                .filter(last -> last.head()._2() == width && block._2() == width)
                                .map(last -> done.tail().prepend(last.prepend(block)))
                                .getOrElse(() -> done.prepend(List.of(block))))
                .map(List::reverse)
                .reverse();
        String definitions = blocks.map(Tuple2::_1)
                .filter(LinkReferenceDefinition.class::isInstance)
                .map(definition -> "\n\n" + source(lines, List.of(definition)))
                .mkString();
        return runs.map(run -> run.head()._2() == width
                ? new Piece(source(lines, run.map(Tuple2::_1)) + definitions, width)
                : new Piece(source(lines, run.map(Tuple2::_1)), run.head()._2()));
    }

    /**
     * The lines of {@code markdown} that {@code blocks}, which follow each other, were parsed from.
     */
    private static String source(Vector<String> markdown, List<Node> blocks) {
        return markdown.subSequence(
                        blocks.head().getSourceSpans().getFirst().getLineIndex(),
                        blocks.last().getSourceSpans().getLast().getLineIndex() + 1)
                .mkString("\n");
    }

    /**
     * How wide {@code block} is drawn: a code block too wide for {@code width} as wide as its widest line and its
     * border, anything else {@code width}.
     */
    private static int wide(Node block, int width) {
        Option<String> code = block instanceof FencedCodeBlock fenced
                ? Option.of(fenced.getLiteral())
                : block instanceof IndentedCodeBlock indented ? Option.of(indented.getLiteral()) : Option.none();
        return code.map(literal ->
                        List.ofAll(literal.lines()).map(TextWidth::of).max().getOrElse(0))
                .map(widest -> widest + 2)
                .filter(needed -> needed > width)
                .getOrElse(width);
    }

    private static List<Line> drawn(
            String markdown,
            int width,
            StylePropertyResolver resolver,
            List<Picture> pictures,
            Option<SyntaxHighlighter> highlighter) {
        MarkdownView.Builder builder = MarkdownView.builder().source(markdown).styleResolver(resolver);
        MarkdownView view =
                highlighter.map(builder::syntaxHighlighter).getOrElse(builder).build();
        int height = view.computeHeight(width);
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        view.render(area, buffer);
        // The lines, and how many cells of each picture they have drawn.
        return List.range(0, height)
                .foldLeft(Tuple.<List<Line>, Map<Integer, Integer>>of(List.empty(), HashMap.empty()), (done, row) -> {
                    Tuple2<Vector<Tuple2<Style, String>>, Map<Integer, Integer>> painted =
                            painted(row(buffer, row, width), pictures, done._2());
                    return Tuple.of(done._1().append(line(painted._1())), painted._2());
                })
                ._1();
    }

    /**
     * The style and symbol of each cell of a {@code row}.
     */
    private static Vector<Tuple2<Style, String>> row(Buffer buffer, int row, int width) {
        return Vector.range(0, width)
                .map(column -> buffer.get(column, row))
                .filter(cell -> !cell.isContinuation())
                .map(cell -> Tuple.of(cell.style(), cell.symbol()));
    }

    /**
     * {@code cells} with each that stands for a cell of one of {@code pictures} that cell, the next after as many as
     * {@code drawn} says are drawn of it; and how many are drawn of each then.
     */
    private static Tuple2<Vector<Tuple2<Style, String>>, Map<Integer, Integer>> painted(
            Vector<Tuple2<Style, String>> cells, List<Picture> pictures, Map<Integer, Integer> drawn) {
        return cells.foldLeft(Tuple.of(Vector.empty(), drawn), (done, cell) -> {
            int index = cell._2().length() == 1 ? cell._2().charAt(0) - MarkdownImages.PICTURE : -1;
            if (index < 0 || index >= pictures.size()) {
                return Tuple.of(done._1().append(cell), done._2());
            }
            int at = done._2().get(index).getOrElse(0);
            return Tuple.of(
                    done._1().append(HalfBlocks.cell(pictures.get(index), at)),
                    done._2().put(index, at + 1));
        });
    }

    /**
     * {@code cells} as a line: each run of cells of one style a span, without the blank cells at its end.
     */
    private static Line line(Vector<Tuple2<Style, String>> cells) {
        int used = cells.lastIndexWhere(
                        cell -> !cell._2().isBlank() || cell._1().bg().isPresent())
                + 1;
        List<Tuple2<Style, String>> runs = cells.take(used)
                .foldLeft(
                        List.<Tuple2<Style, String>>empty(),
                        (done, cell) -> done.headOption()
                                .filter(last -> last._1().equals(cell._1()))
                                .map(last -> done.tail().prepend(Tuple.of(last._1(), last._2() + cell._2())))
                                .getOrElse(() -> done.prepend(cell)))
                .reverse();
        return Line.from(runs.map(run -> Span.styled(run._2(), run._1())).toJavaList());
    }

    /**
     * A part of a text and how wide it is drawn.
     */
    private record Piece(String markdown, int width) {}
}
