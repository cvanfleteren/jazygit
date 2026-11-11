package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
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
import net.vanfleteren.jazygit.ui.widgets.MarkdownImages;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * A version of a Markdown file rendered (ADR 103): top-level block by top-level block, each drawn by itself as tamboui
 * draws Markdown ({@link MarkdownLines}), with the lines of the source it was parsed from. A list is a block for each
 * of its top-level items, and a table, found as GitHub finds one, is drawn by jazygit. Front matter is drawn as a
 * {@code yaml} code block, a comment of HTML is left out, an image is a link that says it is one (ADR 82), and a
 * relative link is its text, as it leads nowhere outside the repository.
 */
final class RenderedMarkdown {

    // Thread-safe: the blocks are drawn side by side.
    private static final Parser PARSER = Parser.builder()
            .extensions(java.util.List.of(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    // A link with a scheme, as https: or mailto:, or to another host; anything else is relative.
    private static final Pattern ABSOLUTE = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*:|//).*", Pattern.DOTALL);
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
        Vector<String> source = withoutComments(withFrontMatter(lines));
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
        List<Tuple2<Tuple2<Node, Boolean>, Integer>> parts = top.filter(
                        block -> !(block instanceof LinkReferenceDefinition))
                .flatMap(RenderedMarkdown::parts)
                .zipWithIndex();
        // Collected, not read through an iterator, so the stream runs on the common pool (RichDiff.inParallel).
        return parts.toJavaParallelStream()
                .map(part -> {
                    Node node = part._1()._1();
                    String text = text(source, node);
                    return new Block(
                            drawn(text, definitions, width, styles, highlighter),
                            first(node),
                            last(node),
                            part._2() > 0 && part._1()._2());
                })
                .collect(List.collector())
                .filter(block -> !block.rows().isEmpty());
    }

    /**
     * The parts {@code block} is drawn in, each with whether a blank line comes before it: each item of a list, which
     * only an item of a loose list has after the first, else the block itself.
     */
    private static List<Tuple2<Node, Boolean>> parts(Node block) {
        if (block instanceof ListBlock list) {
            return children(list)
                    .filter(item -> !item.getSourceSpans().isEmpty())
                    .zipWithIndex()
                    .map(item -> Tuple.of(item._1(), item._2() == 0 || !list.isTight()));
        }
        return List.of(Tuple.of(block, true));
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
        String shown = withoutRelativeLinks(MarkdownImages.asLinks(markdown));
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

    /**
     * {@code lines} with each comment of HTML left out, as commonmark finds them: a block of HTML that is a comment,
     * and a comment in a line of text, but not what looks like one in code. The line breaks in a comment are kept, so
     * each line is where it was.
     */
    static Vector<String> withoutComments(Vector<String> lines) {
        java.util.List<Node> blocks = new java.util.ArrayList<>();
        java.util.List<SourceSpan> inlines = new java.util.ArrayList<>();
        PARSER.parse(lines.mkString("\n")).accept(new AbstractVisitor() {
            @Override
            public void visit(HtmlBlock block) {
                if (block.getLiteral().strip().startsWith("<!--")
                        && !block.getSourceSpans().isEmpty()) {
                    blocks.add(block);
                }
            }

            @Override
            public void visit(HtmlInline inline) {
                if (inline.getLiteral().startsWith("<!--")) {
                    inlines.addAll(inline.getSourceSpans());
                }
            }

            @Override
            public void visit(CustomBlock block) {
                visitChildren(block);
            }

            @Override
            public void visit(CustomNode node) {
                visitChildren(node);
            }
        });
        // A block of HTML that is a comment may hold text after it: only its comments go, their line breaks kept.
        Vector<String> outside = List.ofAll(blocks).foldLeft(lines, (done, block) -> {
            int from = first(block);
            Vector<String> kept = Vector.of(HTML_COMMENT
                    .matcher(done.subSequence(from, last(block) + 1).mkString("\n"))
                    .replaceAll(comment -> "\n".repeat((int)
                            comment.group().chars().filter(c -> c == '\n').count()))
                    .split("\n", -1));
            return kept.zipWithIndex().foldLeft(done, (updated, line) -> updated.update(from + line._2(), line._1()));
        });
        // From the last to the first, so each leaves the columns of those before it on its line.
        return List.ofAll(inlines)
                .sortBy(span -> -(span.getLineIndex() * 100_000L + span.getColumnIndex()))
                .foldLeft(outside, (done, span) -> {
                    String line = done.get(span.getLineIndex());
                    int end = Math.min(line.length(), span.getColumnIndex() + span.getLength());
                    return done.update(
                            span.getLineIndex(), line.substring(0, span.getColumnIndex()) + line.substring(end));
                });
    }

    /**
     * {@code lines} with their front matter, a block of YAML between {@code ---} lines at the top, as a {@code yaml}
     * code block of as many lines.
     */
    static Vector<String> withFrontMatter(Vector<String> lines) {
        return lines.headOption()
                .filter(first -> first.strip().equals("---"))
                .flatMap(first -> lines.zipWithIndex()
                        .drop(1)
                        .find(line -> line._1().strip().equals("---")
                                || line._1().strip().equals("..."))
                        .map(Tuple2::_2))
                .map(end -> lines.update(0, "```yaml").update(end, "```"))
                .getOrElse(lines);
    }

    /**
     * {@code markdown} with each link whose target is relative, to another file of the repository or a heading,
     * replaced by its text: it leads nowhere outside the repository. A link over several lines stays.
     */
    static String withoutRelativeLinks(String markdown) {
        java.util.List<Tuple2<SourceSpan, String>> replaced = new java.util.ArrayList<>();
        Vector<String> lines = Vector.of(markdown.split("\n", -1));
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Link link) {
                if (link.getSourceSpans().size() == 1
                        && !ABSOLUTE.matcher(link.getDestination()).matches()) {
                    SourceSpan span = link.getSourceSpans().getFirst();
                    replaced.add(Tuple.of(span, inner(link, lines)));
                    return;
                }
                visitChildren(link);
            }

            @Override
            public void visit(CustomBlock block) {
                visitChildren(block);
            }

            @Override
            public void visit(CustomNode node) {
                visitChildren(node);
            }
        });
        // From the last to the first, so each replacement leaves the columns of those before it.
        return List.ofAll(replaced)
                .sortBy(entry ->
                        -(entry._1().getLineIndex() * 100_000L + entry._1().getColumnIndex()))
                .foldLeft(lines, (done, entry) -> {
                    SourceSpan span = entry._1();
                    String line = done.get(span.getLineIndex());
                    return done.update(
                            span.getLineIndex(),
                            line.substring(0, span.getColumnIndex())
                                    + entry._2()
                                    + line.substring(span.getColumnIndex() + span.getLength()));
                })
                .mkString("\n");
    }

    /**
     * The source of the text of {@code link}, from where its first child starts to where its last ends, on its line.
     */
    private static String inner(Link link, Vector<String> lines) {
        Node first = link.getFirstChild();
        Node last = link.getLastChild();
        if (first == null
                || first.getSourceSpans().isEmpty()
                || last.getSourceSpans().isEmpty()) {
            return "";
        }
        SourceSpan from = first.getSourceSpans().getFirst();
        SourceSpan to = last.getSourceSpans().getLast();
        if (from.getLineIndex() != to.getLineIndex()) {
            return "";
        }
        return lines.get(from.getLineIndex()).substring(from.getColumnIndex(), to.getColumnIndex() + to.getLength());
    }

    private static List<Node> children(Node node) {
        return List.ofAll(Iterator.iterate(node.getFirstChild(), Node::getNext).takeWhile(Objects::nonNull));
    }

    private static int first(Node node) {
        return node.getSourceSpans().getFirst().getLineIndex();
    }

    private static int last(Node node) {
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
}
