package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.ui.widgets.MarkdownImages;
import org.commonmark.node.AbstractVisitor;
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
 * of its top-level items. Front matter is drawn as a {@code yaml} code block, a comment of HTML is left out, an image is
 * a link that says it is one (ADR 82), and a relative link is its text, as it leads nowhere outside the repository.
 */
final class RenderedMarkdown {

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    // A link with a scheme, as https: or mailto:, or to another host; anything else is relative.
    private static final Pattern ABSOLUTE = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*:|//).*", Pattern.DOTALL);

    private RenderedMarkdown() {}

    /**
     * The blocks of the Markdown {@code lines}, each drawn {@code width} columns wide, or as wide as a code block or a
     * table too wide for that, styled as {@code styles} says, its code blocks coloured by jazygit's lexer. A block that
     * draws nothing, as a link definition, is left out.
     */
    static List<Block> blocks(Vector<String> lines, int width, Theme.SpanStyles styles) {
        Vector<String> source = withFrontMatter(withoutComments(lines));
        Node document = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                .build()
                .parse(source.mkString("\n"));
        List<Node> top = List.ofAll(Iterator.iterate(document.getFirstChild(), Node::getNext)
                        .takeWhile(Objects::nonNull))
                .filter(block -> !block.getSourceSpans().isEmpty());
        String definitions = top.filter(LinkReferenceDefinition.class::isInstance)
                .map(definition -> "\n\n" + text(source, definition))
                .mkString();
        LexedHighlighter highlighter = new LexedHighlighter(styles);
        return top.filter(block -> !(block instanceof LinkReferenceDefinition))
                .flatMap(RenderedMarkdown::parts)
                .zipWithIndex()
                .map(part -> new Block(
                        drawn(text(source, part._1()._1()) + definitions, width, styles, highlighter),
                        first(part._1()._1()),
                        last(part._1()._1()),
                        part._2() > 0 && part._1()._2()))
                .filter(block -> !block.rows().isEmpty());
    }

    /**
     * The parts {@code block} is drawn in, each with whether a blank line comes before it: each item of a list, which
     * only an item of a loose list has after the first, else the block itself.
     */
    private static List<Tuple2<Node, Boolean>> parts(Node block) {
        if (block instanceof ListBlock list) {
            return List.ofAll(Iterator.iterate(list.getFirstChild(), Node::getNext)
                            .takeWhile(Objects::nonNull))
                    .filter(item -> !item.getSourceSpans().isEmpty())
                    .zipWithIndex()
                    .map(item -> Tuple.of(item._1(), item._2() == 0 || !list.isTight()));
        }
        return List.of(Tuple.of(block, true));
    }

    /**
     * {@code markdown} drawn, its images links that say so and its relative links their text, without the blank lines
     * at its end.
     */
    private static List<Line> drawn(String markdown, int width, Theme.SpanStyles styles, LexedHighlighter highlighter) {
        List<Line> rows = MarkdownLines.markdown(
                withoutRelativeLinks(MarkdownImages.asLinks(markdown)),
                width,
                styles.markdown(),
                List.empty(),
                Option.of(highlighter));
        return rows.reverse().dropWhile(row -> row.width() == 0).reverse();
    }

    /**
     * {@code lines} with each comment of HTML left out, but the line breaks in it kept, so each line is where it was.
     */
    static Vector<String> withoutComments(Vector<String> lines) {
        Matcher matcher = HTML_COMMENT.matcher(lines.mkString("\n"));
        StringBuilder kept = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(kept, Matcher.quoteReplacement("\n".repeat((int)
                            matcher.group().chars().filter(c -> c == '\n').count())));
        }
        matcher.appendTail(kept);
        return Vector.of(kept.toString().split("\n", -1));
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
        Node document = Parser.builder()
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build()
                .parse(markdown);
        java.util.List<Tuple2<SourceSpan, String>> replaced = new java.util.ArrayList<>();
        Vector<String> lines = Vector.of(markdown.split("\n", -1));
        document.accept(new AbstractVisitor() {
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
        });
        // From the last to the first, so each replacement leaves the columns of those before it.
        return List.ofAll(replaced)
                .sortBy(entry ->
                        -(entry._1().getLineIndex() * 100_000 + entry._1().getColumnIndex()))
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
