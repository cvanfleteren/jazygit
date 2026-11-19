package net.vanfleteren.jazygit.content.diff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;

/**
 * The source of a version of a Markdown file as it is drawn (ADR 103): without its comments of HTML or a byte order
 * mark, its front matter as a {@code yaml} code block, and a relative link as its text. Each keeps the lines as many,
 * so each block keeps the lines it was parsed from. Only functions.
 */
final class MarkdownSource {

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    // A link with a scheme, as https: or mailto:, or to another host; anything else is relative.
    private static final Pattern ABSOLUTE = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*:|//).*", Pattern.DOTALL);
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private MarkdownSource() {}

    /**
     * {@code lines} with each comment of HTML left out, as commonmark finds them: a block of HTML that is a comment,
     * and a comment in a line of text, but not what looks like one in code. The line breaks in a comment are kept, so
     * each line is where it was.
     */
    static Vector<String> withoutComments(Vector<String> lines) {
        java.util.List<Node> blocks = new java.util.ArrayList<>();
        java.util.List<SourceSpan> inlines = new java.util.ArrayList<>();
        RenderedMarkdown.PARSER.parse(lines.mkString("\n")).accept(new AbstractVisitor() {
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
            int from = RenderedMarkdown.first(block);
            Vector<String> kept = Vector.of(HTML_COMMENT
                    .matcher(done.subSequence(from, RenderedMarkdown.last(block) + 1)
                            .mkString("\n"))
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
     * {@code lines} without the byte order mark the first of them starts with, if it does: as GitHub has it, it is no
     * part of the text, and commonmark would take it for one, so a heading, a list or front matter on the first line
     * would not be one. The lines stay as many, so each block keeps the lines it was parsed from.
     */
    static Vector<String> withoutByteOrderMark(Vector<String> lines) {
        return lines.headOption()
                .filter(first -> first.startsWith(BYTE_ORDER_MARK))
                .map(first -> lines.update(0, first.substring(1)))
                .getOrElse(lines);
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
        RenderedMarkdown.PARSER.parse(markdown).accept(new AbstractVisitor() {
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
}
