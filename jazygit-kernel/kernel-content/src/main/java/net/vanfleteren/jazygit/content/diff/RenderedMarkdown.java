package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.Iterator;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.ui.markdown.MarkdownImages;
import net.vanfleteren.jazygit.ui.markdown.MarkdownLines;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * A version of a Markdown file rendered (ADR 103): top-level block by top-level block, each drawn by itself as tamboui
 * draws Markdown ({@link MarkdownLines}), with the lines of the source it was parsed from. A list is a block for each
 * of its top-level items, and a table, found as GitHub finds one, is drawn by jazygit. Front matter is drawn as a
 * {@code yaml} code block, a comment of HTML is left out, an image is a link that says it is one (ADR 82), and a
 * relative link is its text, as it leads nowhere outside the repository: what {@link MarkdownSource} makes of the
 * source before it is drawn.
 */
final class RenderedMarkdown {

    // Thread-safe: the blocks are drawn side by side.
    static final Parser PARSER = Parser.builder()
            .extensions(java.util.List.of(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    // What starts a block other than a paragraph on a line by itself: an item of a list, a heading, a quote, a fence.
    private static final Pattern BLOCK_START = Pattern.compile("[-+*](\\s|$)|#{1,6}(\\s|$)|>|`{3}|~{3}");
    // An item of an ordered list: its number, the dot or parenthesis after it, and the rest.
    private static final Pattern ORDERED = Pattern.compile("(\\d{1,9})([.)])(\\s.*|)");
    // A thematic break: three or more of one of -, * and _, with blanks between them.
    private static final Pattern THEMATIC = Pattern.compile("([-*_])(\\s*\\1){2,}\\s*");
    private static final String COLUMN_GAP = "  ";

    private RenderedMarkdown() {}

    /**
     * The blocks of the Markdown {@code lines}, each drawn {@code width} columns wide, or as wide as a code block or a
     * table too wide for that, styled as {@code styles} says, its code blocks coloured by jazygit's lexer. A block that
     * draws nothing, as a link definition, is left out. Each block is drawn with only the link definitions it refers
     * to, and the blocks are drawn side by side: a changelog of hundreds of releases has as many blocks and definitions.
     */
    static List<Block> blocks(Vector<String> lines, int width, Theme.SpanStyles styles) {
        Vector<String> source = MarkdownSource.withoutComments(
                MarkdownSource.withFrontMatter(MarkdownSource.withoutByteOrderMark(lines)));
        Node document = PARSER.parse(source.mkString("\n"));
        List<Node> top =
                children(document).filter(block -> !block.getSourceSpans().isEmpty());
        // Of two definitions with one label, the first holds, as in commonmark.
        Map<String, String> definitions = top.filter(LinkReferenceDefinition.class::isInstance)
                .map(LinkReferenceDefinition.class::cast)
                .foldLeft(
                        LinkedHashMap.empty(),
                        (done, definition) -> done.containsKey(label(definition.getLabel()))
                                ? done
                                : done.put(label(definition.getLabel()), text(source, definition)));
        LexedHighlighter highlighter = new LexedHighlighter(styles);
        // The first part has no blank line before it.
        List<Part> parts = top.filter(block -> !(block instanceof LinkReferenceDefinition))
                .flatMap(RenderedMarkdown::parts)
                .zipWithIndex((part, index) -> new Part(part.node(), index > 0 && part.spaced()));
        // Collected, not read through an iterator, so the stream runs on the common pool (RichDiff.inParallel).
        return parts.toJavaParallelStream()
                .map(part -> {
                    Node node = part.node();
                    String text = text(source, node);
                    return new Block(
                            drawn(text, definitions, width, styles, highlighter),
                            first(node),
                            last(node),
                            part.spaced());
                })
                .collect(List.collector())
                .filter(block -> !block.rows().isEmpty());
    }

    /**
     * The parts {@code block} is drawn in, each with whether a blank line comes before it: each item of a list, which
     * only an item of a loose list has after the first, else the block itself.
     */
    private static List<Part> parts(Node block) {
        if (block instanceof ListBlock list) {
            return children(list)
                    .filter(item -> !item.getSourceSpans().isEmpty())
                    .zipWithIndex((item, index) -> new Part(item, index == 0 || !list.isTight()));
        }
        return List.of(new Part(block, true));
    }

    /**
     * The link definitions of {@code definitions}, by label, that {@code markdown} may refer to, as Markdown to append
     * to it: those whose label it holds between brackets, whatever their case.
     */
    static String referred(String markdown, Map<String, String> definitions) {
        // As a label is matched: its blanks and line breaks one space, none just inside its brackets, whatever its
        // case.
        String text = markdown.replaceAll("\\s+", " ")
                .replace("[ ", "[")
                .replace(" ]", "]")
                .toLowerCase(Locale.ROOT);
        return definitions
                .filterKeys(label -> text.contains("[" + label + "]"))
                .values()
                .map(definition -> "\n\n" + definition)
                .mkString();
    }

    /**
     * A label of a link as commonmark matches it: its blanks one space, whatever its case.
     */
    private static String label(String label) {
        return label.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * {@code markdown} drawn with those of the link {@code definitions} it refers to, its images links that say so and
     * its relative links their text, without the blank lines at its end; a table as {@link #table} draws it.
     */
    private static List<Line> drawn(
            String markdown,
            Map<String, String> definitions,
            int width,
            Theme.SpanStyles styles,
            LexedHighlighter highlighter) {
        String shown = MarkdownSource.withoutRelativeLinks(MarkdownImages.asLinks(markdown));
        List<Line> rows = table(shown, definitions, styles)
                .getOrElse(() -> MarkdownLines.markdown(
                        shown + referred(shown, definitions),
                        width,
                        styles.markdown(),
                        List.empty(),
                        Option.of(highlighter)));
        return rows.reverse().dropWhile(row -> row.width() == 0).reverse();
    }

    /**
     * {@code markdown} drawn as a table, if it is one as GitHub has it: a row of as many cells between pipes as the row
     * of dashes under it, a pipe escaped or in code being no pipe between cells. Each column is as wide as its widest
     * cell, its cells aligned as the colons of its dashes say, two spaces between two columns, the cells of the first
     * row bold, each cell drawn as tamboui draws the Markdown in it, with those of the link {@code definitions}, by
     * label, it refers to, but only as text: a cell holds no block. So a table wider than the area scrolls sideways with it, as a code block
     * does. Not: tamboui's table, which gives each column as many columns of the area, and cuts off what does not fit.
     */
    static Option<List<Line>> table(String markdown, Map<String, String> definitions, Theme.SpanStyles styles) {
        Vector<String> lines = Vector.of(markdown.split("\n", -1));
        return Option.of(PARSER.parse(markdown).getFirstChild())
                .filter(TableBlock.class::isInstance)
                .map(table -> {
                    // The rows of the head and the body, each a list of its cells.
                    Vector<Vector<TableCell>> rows = children(table)
                            .flatMap(RenderedMarkdown::children)
                            .map(row -> children(row)
                                    .filter(TableCell.class::isInstance)
                                    .map(TableCell.class::cast)
                                    .toVector())
                            .toVector();
                    int columns = rows.map(Vector::size).max().getOrElse(0);
                    // The source of each row: its line, but the one of dashes under the first.
                    Vector<String> sources = lines.removeAt(1).take(rows.size());
                    Vector<Vector<List<Span>>> drawn = rows.zip(sources)
                            .zipWithIndex()
                            .map(row -> Vector.range(0, columns)
                                    .map(column -> cells(row._1()._2())
                                            .lift()
                                            .apply(column)
                                            .map(cell -> inline(cell, definitions, styles, row._2() == 0))
                                            .getOrElse(List.empty())));
                    Vector<Option<TableCell.Alignment>> alignments = Vector.range(0, columns)
                            .map(column -> rows.headOption()
                                    .flatMap(head -> head.lift().apply(column))
                                    .flatMap(cell -> Option.of(cell.getAlignment())));
                    Vector<Integer> widths = Vector.range(0, columns)
                            .map(column -> drawn.map(row -> width(row.get(column)))
                                    .max()
                                    .getOrElse(0));
                    return drawn.map(row -> line(row, widths, alignments)).toList();
                });
    }

    /**
     * The cells of a row of a table as a line: each padded to the width of its column as it is aligned, two spaces
     * between two, no blanks after the last.
     */
    private static Line line(
            Vector<List<Span>> cells, Vector<Integer> widths, Vector<Option<TableCell.Alignment>> alignments) {
        return Line.from(Vector.range(0, cells.size())
                .flatMap(column -> {
                    List<Span> cell = cells.get(column);
                    int room = widths.get(column) - width(cell);
                    int before = alignments
                            .get(column)
                            .map(alignment -> switch (alignment) {
                                case LEFT -> 0;
                                case CENTER -> room / 2;
                                case RIGHT -> room;
                            })
                            .getOrElse(0);
                    boolean last = column == cells.size() - 1;
                    return List.of(Span.raw(column == 0 ? "" : COLUMN_GAP))
                            .append(Span.raw(" ".repeat(before)))
                            .appendAll(cell)
                            .append(Span.raw(last ? "" : " ".repeat(room - before)));
                })
                .filter(span -> !span.content().isEmpty())
                .toJavaList());
    }

    /**
     * The cells of the row {@code line} of a table, as GitHub cuts it: between its pipes, but not an escaped one, even in
     * code, each stripped, an escaped pipe in it a pipe.
     */
    private static Vector<String> cells(String line) {
        String row = line.strip();
        String inner = row.substring(
                row.startsWith("|") ? 1 : 0,
                row.endsWith("|") && !row.endsWith("\\|") && row.length() > 1 ? row.length() - 1 : row.length());
        return Vector.of(inner.split("(?<!\\\\)\\|", -1))
                .map(cell -> cell.strip().replace("\\|", "|"));
    }

    /**
     * The Markdown of a cell drawn on one line, as tamboui draws it, with the link {@code definitions} it refers to,
     * bold if it is in the {@code header}. A cell holds text only, so what would start a block, as {@code -} for none,
     * is drawn as it is.
     */
    private static List<Span> inline(
            String cell, Map<String, String> definitions, Theme.SpanStyles styles, boolean header) {
        String text = inlineOnly(cell);
        List<Span> spans = text.isEmpty()
                ? List.empty()
                : MarkdownLines.markdown(
                                text + referred(text, definitions), Math.max(1, TextWidth.of(text)), styles.markdown())
                        .headOption()
                        .map(line -> List.ofAll(line.spans()))
                        .getOrElse(List.empty());
        return header
                ? spans.map(span -> Span.styled(span.content(), span.style().bold()))
                : spans;
    }

    /**
     * {@code cell} with what would start a block other than a paragraph escaped, so it is drawn as text: the marker of
     * an item of a list, of a heading, of a quote, of a fence, or a thematic break.
     */
    static String inlineOnly(String cell) {
        Matcher ordered = ORDERED.matcher(cell);
        if (ordered.matches()) {
            return ordered.group(1) + "\\" + ordered.group(2) + ordered.group(3);
        }
        return BLOCK_START.matcher(cell).lookingAt() || THEMATIC.matcher(cell).matches() ? "\\" + cell : cell;
    }

    private static int width(List<Span> spans) {
        return spans.map(Span::width).sum().intValue();
    }

    private static List<Node> children(Node node) {
        return List.ofAll(Iterator.iterate(node.getFirstChild(), Node::getNext).takeWhile(Objects::nonNull));
    }

    static int first(Node node) {
        return node.getSourceSpans().getFirst().getLineIndex();
    }

    static int last(Node node) {
        return node.getSourceSpans().getLast().getLineIndex();
    }

    /**
     * The lines of {@code source} {@code node} was parsed from.
     */
    private static String text(Vector<String> source, Node node) {
        return source.subSequence(first(node), last(node) + 1).mkString("\n");
    }

    /**
     * A top-level block drawn: its {@code rows}, the indexes of the {@code first} and the {@code last} line of the
     * source it was parsed from, and whether a blank line sets it apart from the block before it, as a paragraph is,
     * but not an item of a tight list from the item before it.
     */
    record Block(List<Line> rows, int first, int last, boolean spaced) {}

    /**
     * A part a block is drawn in: its {@code node}, and whether a blank line comes before it, {@code spaced}.
     */
    private record Part(Node node, boolean spaced) {}
}
