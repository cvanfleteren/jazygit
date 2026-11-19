package net.vanfleteren.jazygit.content.diff;

import static net.vanfleteren.jazygit.content.diff.TreeText.*;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.diff.RenderedTree.TreeRow;
import net.vanfleteren.jazygit.state.diff.Tree;
import net.vanfleteren.jazygit.state.diff.TreeFolds;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * Draws the rows of the nodes of XML, open as {@code chose} says, styled as {@code styles} says: an element with its
 * attributes, its text, its comments, and its records as a table (ADR 151).
 */
record XmlDrawing(Map<String, Boolean> chose, Theme.SpanStyles styles) {

    /**
     * The rows of {@code nodes}, those at the top of a file of XML.
     */
    List<TreeRow> nodes(List<Tree> nodes) {
        return TreeFolds.xpaths(nodes, "").flatMap(node -> xml(node, "", ""));
    }

    /**
     * The rows of a node of XML, {@code node} at its path, after the guides {@code guides}, what it holds after the
     * guides {@code inner} (ADR 151).
     */
    private List<TreeRow> xml(TreeFolds.Placed node, String guides, String inner) {
        String pointer = node.pointer();
        return switch (node.node()) {
            case Tree.Element element -> element(element, pointer, guides, inner);
            case Tree.Text text -> textRows(text, pointer, guides, inner);
            case Tree.Comment comment -> comment(comment, pointer, guides, inner);
            default -> List.empty();
        };
    }

    /**
     * The rows of what an element of XML at {@code pointer} holds, each after the guides {@code prefix}.
     */
    private List<TreeRow> xmlChildren(List<Tree> children, String pointer, String prefix) {
        List<TreeFolds.Placed> nodes = TreeFolds.xpaths(children, pointer);
        return nodes.zipWithIndex().flatMap(at -> {
            boolean last = at._2 == nodes.size() - 1;
            return xml(at._1, prefix + connector(last, false), prefix + continued(last, false));
        });
    }

    private List<TreeRow> element(Tree.Element element, String pointer, String guides, String inner) {
        List<Span> head = List.of(dim(guides), Span.styled(element.name(), styles.of(Theme.Classes.SYNTAX_KEYWORD)))
                .appendAll(element.attributes().flatMap(this::attribute));
        Option<Tree.Text> text = TreeFolds.text(element);
        if (TreeFolds.oneRow(element)) {
            List<Span> after = text.map(only -> List.of(Span.raw(GAP)).appendAll(textSpans(only, 0)))
                    .getOrElse(List.empty());
            return List.of(
                    new TreeRow(line(head.appendAll(after)), Option.of(pointer), range(element.line(), element.end())));
        }
        boolean open = TreeFolds.open(chose, pointer, element);
        int size = TreeFolds.size(element);
        String count = text.isDefined() ? LINES + size : TreeFolds.alike(element) ? "[" + size + "]" : "{" + size + "}";
        List<Span> closed = open ? List.empty() : List.of(Span.raw(GAP), dim(preview(element)));
        TreeRow row = new TreeRow(
                line(head.append(Span.raw(" ")).append(dim(count)).appendAll(closed)),
                Option.of(pointer),
                open ? range(element.line(), element.tagEnd()) : range(element.line(), element.end()));
        if (!open) {
            return List.of(row);
        }
        if (text.isDefined()) {
            Tree.Text only = text.get();
            return only.lines()
                    .zipWithIndex((written, at) -> new TreeRow(
                            line(List.of(dim(inner + BLOCK_INDENT), Span.raw(flat(written)))),
                            Option.of(TreeFolds.child(pointer, String.valueOf(at))),
                            List.of(only.line() + at)))
                    .prepend(row);
        }
        Option<List<String>> columns = TreeFolds.columns(element);
        return (columns.isDefined()
                        ? xmlTable(element, columns.get(), pointer, inner)
                        : xmlChildren(element.children(), pointer, inner))
                .prepend(row);
    }

    /**
     * The spans of an attribute, after a blank: its value in quotes, a declaration of a namespace dim.
     */
    private List<Span> attribute(Tree.Attribute attribute) {
        String value = "\"" + flat(attribute.value()) + "\"";
        return attribute.namespace()
                ? List.of(Span.raw(" "), dim(attribute.name() + "=" + value))
                : List.of(
                        Span.raw(" "),
                        Span.raw(attribute.name() + "="),
                        Span.styled(value, styles.of(Theme.Classes.SYNTAX_STRING)));
    }

    /**
     * The spans of the line {@code at} of {@code text}, a line break referred to in it as {@value TreeText#NEWLINE}: the first
     * after a dim mark when it is a section of CDATA.
     */
    private List<Span> textSpans(Tree.Text text, int at) {
        Span written = Span.raw(flat(text.lines().get(at)));
        return text.cdata() && at == 0 ? List.of(dim(CDATA + " "), written) : List.of(written);
    }

    /**
     * The rows of a text among elements: a row for each of its lines, each standing for its own line.
     */
    private List<TreeRow> textRows(Tree.Text text, String pointer, String guides, String inner) {
        return text.lines()
                .zipWithIndex((written, at) -> new TreeRow(
                        line(List.of(dim(at == 0 ? guides : inner)).appendAll(textSpans(text, at))),
                        Option.of(at == 0 ? pointer : TreeFolds.child(pointer, String.valueOf(at))),
                        List.of(text.line() + at)));
    }

    /**
     * The rows of a comment: one, dim, or, of more lines, a node of a row for each.
     */
    private List<TreeRow> comment(Tree.Comment comment, String pointer, String guides, String inner) {
        if (!TreeFolds.folds(comment)) {
            return List.of(new TreeRow(
                    line(List.of(dim(guides), dim(oneComment(comment)))),
                    Option.of(pointer),
                    range(comment.line(), comment.end())));
        }
        boolean open = TreeFolds.open(chose, pointer, comment);
        List<Span> closed = open ? List.empty() : List.of(Span.raw(GAP), dim(firstLine(comment.lines())));
        TreeRow row = new TreeRow(
                line(List.of(
                                dim(guides),
                                dim(COMMENT_OPEN + " " + LINES + comment.lines().size()))
                        .appendAll(closed)),
                Option.of(pointer),
                open ? List.of(comment.line()) : range(comment.line(), comment.end()));
        if (!open) {
            return List.of(row);
        }
        return comment.lines()
                .zipWithIndex((written, at) -> new TreeRow(
                        line(List.of(dim(inner + BLOCK_INDENT), dim(written))),
                        Option.of(TreeFolds.child(pointer, String.valueOf(at))),
                        List.of(comment.first() + at)))
                .prepend(row);
    }

    /**
     * The element {@code element} drawn as a table with the {@code columns}: their names, then a row of each of its
     * elements, numbered from 1, a comment between them a row of its own.
     */
    private List<TreeRow> xmlTable(Tree.Element element, List<String> columns, String pointer, String prefix) {
        List<TreeFolds.Placed> nodes = TreeFolds.xpaths(element.children(), pointer);
        List<Tree.Element> records = element.children()
                .filter(child -> child instanceof Tree.Element)
                .map(Tree.Element.class::cast);
        // A vector, not a list: each row of a table of thousands looks its cells up by its index.
        Vector<Vector<String>> cells = records.map(record -> columns.map(column -> flat(TreeFolds.cell(record, column)))
                        .toVector())
                .toVector();
        List<Integer> widths = columns.zipWithIndex((name, column) -> cells.map(row -> TextWidth.of(row.get(column)))
                .append(TextWidth.of(name))
                .max()
                .getOrElse(0));
        int digits = String.valueOf(records.size()).length();
        int lastColumn = columns.size() - 1;
        TreeRow names = new TreeRow(
                line(List.of(dim(prefix + GUIDES.vertical()), Span.raw(" ".repeat(digits) + GAP))
                        .appendAll(columns.zipWithIndex((name, column) ->
                                Span.raw(column == lastColumn ? name : pad(name, widths.get(column)) + COLUMN_GAP)))),
                Option.none(),
                List.of(element.tagEnd()));
        // Each node with the number of the records up to it, its own too, as its index.
        Vector<Integer> numbers = nodes.toVector()
                .scanLeft(0, (count, node) -> count + (node.node() instanceof Tree.Element ? 1 : 0))
                .tail();
        return nodes.zipWithIndex((node, at) -> {
                    boolean last = at == nodes.size() - 1;
                    String guides = prefix + connector(last, false);
                    if (node.node() instanceof Tree.Comment comment) {
                        return comment(comment, node.pointer(), guides, prefix + continued(last, false))
                                .head();
                    }
                    Tree.Element record = (Tree.Element) node.node();
                    int number = numbers.get(at);
                    Vector<String> row = cells.get(number - 1);
                    List<Span> shown = columns.zipWithIndex((name, column) ->
                            xmlCell(name, row.get(column), column == lastColumn ? 0 : widths.get(column)));
                    return new TreeRow(
                            line(List.of(dim(guides), dim(padLeft(String.valueOf(number), digits)), Span.raw(GAP))
                                    .appendAll(shown)),
                            Option.of(node.pointer()),
                            range(record.line(), record.end()));
                })
                .prepend(names);
    }

    /**
     * A cell of a table of XML, {@code width} columns wide and a gap after it, or as wide as it is when it is last:
     * the value of an attribute coloured as a string, text plain.
     */
    private Span xmlCell(String column, String text, int width) {
        String shown = width == 0 ? text : pad(text, width) + COLUMN_GAP;
        return column.startsWith("@") ? Span.styled(shown, styles.of(Theme.Classes.SYNTAX_STRING)) : Span.raw(shown);
    }

    private Span dim(String text) {
        return Span.styled(text, styles.of(Theme.Classes.DIM));
    }
}
