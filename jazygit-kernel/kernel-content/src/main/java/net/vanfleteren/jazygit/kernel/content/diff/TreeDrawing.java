package net.vanfleteren.jazygit.kernel.content.diff;

import static net.vanfleteren.jazygit.kernel.content.diff.TreeText.*;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.content.diff.RenderedTree.TreeRow;
import net.vanfleteren.jazygit.kernel.state.diff.Tree;
import net.vanfleteren.jazygit.kernel.state.diff.TreeFolds;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * Draws the rows of a tree of JSON or YAML, {@code width} columns wide, its nodes open as {@code chose} says, styled as
 * {@code styles} says: a container of scalars that fits on one line, a long sequence of scalars packed, a sequence of
 * records as a table, a block as its lines, each document of a file of more than one as a node (ADR 122, 123). The
 * nodes of XML it leaves to {@link XmlDrawing} (ADR 151).
 */
record TreeDrawing(int width, Map<String, Boolean> chose, Theme.SpanStyles styles) {

    /**
     * The rows of {@code documents}: one drawn as its root, more than one each as a node of its own.
     */
    List<TreeRow> documents(List<Tree.Document> documents) {
        if (!TreeFolds.apart(documents)) {
            return root(documents.head().root());
        }
        return documents.zipWithIndex().flatMap(at -> document(at._1, at._2));
    }

    List<TreeRow> root(Tree root) {
        if (root instanceof Tree.Markup markup) {
            return new XmlDrawing(chose, styles).nodes(markup.nodes());
        }
        if (TreeFolds.leaf(root)) {
            return List.of(new TreeRow(line(leaf(root)), Option.of(""), List.of(root.line())));
        }
        if (root instanceof Tree.Block block) {
            return lines(block, "", "");
        }
        if (TreeFolds.size(root) == 0) {
            return List.of(new TreeRow(
                    line(List.of(dim(properties(root).text() + (root instanceof Tree.Mapping ? "{}" : "[]")))),
                    Option.of(""),
                    List.of(root.line())));
        }
        return children(root, "", "", true);
    }

    /**
     * The rows of the {@code index}th document of a file of more than one: a row of its own, {@code --- 1}, named
     * as a mapping in a sequence is, but by {@code kind} first, then what its root holds when it is open.
     */
    private List<TreeRow> document(Tree.Document document, int index) {
        String pointer = TreeFolds.child("", String.valueOf(index));
        Tree root = document.root();
        Span label = dim("--- " + (index + 1));
        if (!TreeFolds.foldsApart(document)) {
            List<Span> value = TreeFolds.leaf(root)
                    ? leaf(root)
                    : root instanceof Tree.Block block
                            ? List.of(dim(block.properties().text() + count(block)))
                            : List.of(dim(properties(root).text() + (root instanceof Tree.Mapping ? "{}" : "[]")));
            return List.of(new TreeRow(
                    line(List.of(label, Span.raw(GAP)).appendAll(value)),
                    Option.of(pointer),
                    range(document.line(), root.end())));
        }
        boolean open = TreeFolds.open(chose, pointer, root);
        Option<Tree.Field> naming = root instanceof Tree.Mapping mapping
                ? TreeFolds.naming(mapping, TreeFolds.DOCUMENT_NAMING)
                : Option.none();
        Tree shown =
                naming.map(field -> (Tree) without((Tree.Mapping) root, field)).getOrElse(root);
        TreeRow row = new TreeRow(
                line(List.of(label)
                        .appendAll(propertiesOf(root))
                        .appendAll(named(naming))
                        .appendAll(List.of(Span.raw(" "), dim(count(root))))
                        .appendAll(open ? List.empty() : List.of(Span.raw(GAP), dim(preview(shown))))),
                Option.of(pointer),
                open ? List.of(document.line()) : range(document.line(), root.end()));
        return open ? children(shown, pointer, "", false).prepend(row) : List.of(row);
    }

    /**
     * The rows of what {@code tree} at {@code pointer} holds, after the guides {@code prefix}; at the top, with no
     * connectors of their own.
     */
    private List<TreeRow> children(Tree tree, String pointer, String prefix, boolean top) {
        Option<List<String>> table = TreeFolds.table(tree);
        if (table.isDefined()) {
            return table((Tree.Sequence) tree, table.get(), pointer, prefix, top);
        }
        if (TreeFolds.packed(tree)) {
            return packed((Tree.Sequence) tree, pointer, prefix, top);
        }
        List<Entry> entries = entries(tree, pointer);
        int digits = String.valueOf(Math.max(0, entries.size() - 1)).length();
        return entries.zipWithIndex().flatMap(at -> {
            Entry entry = at._1;
            boolean last = at._2 == entries.size() - 1;
            Span label = entry.key().map(key -> Span.raw(key)).getOrElse(() -> dim(pad(String.valueOf(at._2), digits)));
            return entry(entry, label, prefix + connector(last, top), prefix + continued(last, top));
        });
    }

    /**
     * The rows of {@code entry}, its label {@code label} after the guides {@code guides}, what it holds after the
     * guides {@code inner}.
     */
    private List<TreeRow> entry(Entry entry, Span label, String guides, String inner) {
        Tree value = entry.value();
        List<Span> head = List.of(dim(guides), label);
        List<Integer> ignored = entry.ignored();
        Span note = ignored.isEmpty()
                ? Span.raw("")
                : dim(GAP
                        + Messages.get(
                                ignored.size() == 1 ? "content.json.duplicate" : "content.json.duplicates",
                                ignored.mkString(", ")));
        if (TreeFolds.leaf(value)) {
            return List.of(new TreeRow(
                    line(head.append(Span.raw(GAP)).appendAll(leaf(value)).append(note)),
                    Option.of(entry.pointer()),
                    range(entry.line(), value.end()).appendAll(ignored)));
        }
        if (TreeFolds.oneLine(value)) {
            return List.of(new TreeRow(
                    line(head.appendAll(propertiesOf(value))
                            .append(Span.raw(GAP))
                            .appendAll(oneLine(value))
                            .append(note)),
                    Option.of(entry.pointer()),
                    range(entry.line(), value.end()).appendAll(ignored)));
        }
        boolean open = TreeFolds.open(chose, entry.pointer(), value);
        Option<Tree.Field> naming = entry.key().isEmpty() && value instanceof Tree.Mapping mapping
                ? TreeFolds.naming(mapping, TreeFolds.NAMING)
                : Option.none();
        // The field that names it is not drawn again in it, nor in its preview.
        Tree shown =
                naming.map(field -> (Tree) without((Tree.Mapping) value, field)).getOrElse(value);
        List<Span> count = List.of(Span.raw(" "), dim(count(value)));
        List<Span> preview = open ? List.empty() : List.of(Span.raw(GAP), dim(preview(shown)));
        TreeRow row = new TreeRow(
                line(head.appendAll(propertiesOf(value))
                        .appendAll(named(naming))
                        .appendAll(count)
                        .appendAll(preview)
                        .append(note)),
                Option.of(entry.pointer()),
                (open ? List.of(entry.line()) : range(entry.line(), value.end())).appendAll(ignored));
        if (!open) {
            return List.of(row);
        }
        if (value instanceof Tree.Block block) {
            return lines(block, entry.pointer(), inner + BLOCK_INDENT).prepend(row);
        }
        return children(shown, entry.pointer(), inner, false).prepend(row);
    }

    /**
     * The rows of the lines of {@code block} at {@code pointer}, each after the guides {@code guides}, each
     * standing for its own line.
     */
    private List<TreeRow> lines(Tree.Block block, String pointer, String guides) {
        return block.lines()
                .zipWithIndex((text, at) -> new TreeRow(
                        line(List.of(dim(guides), Span.styled(text, styles.of(Theme.Classes.SYNTAX_STRING)))),
                        Option.of(TreeFolds.child(pointer, String.valueOf(at))),
                        List.of(block.line() + 1 + at)));
    }

    /**
     * The spans of the field that names a mapping, after a gap, if it has one.
     */
    private List<Span> named(Option<Tree.Field> naming) {
        return naming.map(
                        field -> List.of(Span.raw(GAP), dim(field.key() + ": ")).appendAll(leaf(field.value())))
                .getOrElse(List.empty());
    }

    /**
     * The tag and the anchor of {@code tree}, dim, after a blank, when it has any.
     */
    private List<Span> propertiesOf(Tree tree) {
        String text = properties(tree).text().strip();
        return text.isEmpty() ? List.empty() : List.of(Span.raw(" "), dim(text));
    }

    /**
     * The sequence {@code sequence} drawn as a table with the {@code columns}: the keys, then a row of each of its
     * mappings.
     */
    private List<TreeRow> table(
            Tree.Sequence sequence, List<String> columns, String pointer, String prefix, boolean top) {
        // A vector, not a list: each row of a table of thousands looks its cells up by its index.
        Vector<Vector<Tree>> cells = sequence.elements()
                .map(element ->
                        ((Tree.Mapping) element).fields().map(Tree.Field::value).toVector())
                .toVector();
        int size = cells.size();
        List<Integer> widths =
                columns.zipWithIndex((key, column) -> cells.map(row -> TextWidth.of(cellText(row.get(column))))
                        .append(TextWidth.of(key))
                        .max()
                        .getOrElse(0));
        int digits = String.valueOf(sequence.elements().size() - 1).length();
        String index = " ".repeat(digits) + GAP;
        TreeRow keys = new TreeRow(
                line(List.of(dim(prefix + (top ? "" : GUIDES.vertical())), Span.raw(index))
                        .appendAll(columns.zipWithIndex((key, column) -> List.of(
                                        Span.raw(pad(key, widths.get(column))),
                                        Span.raw(column == columns.size() - 1 ? "" : COLUMN_GAP)))
                                .flatMap(spans -> spans))),
                Option.none(),
                List.of(sequence.line()));
        return sequence.elements()
                .zipWithIndex((element, at) -> {
                    boolean last = at == size - 1;
                    List<Span> row = cells.get(at)
                            .toList()
                            .zipWithIndex((cell, column) -> List.of(
                                    cell(cell, widths.get(column)),
                                    Span.raw(column == columns.size() - 1 ? "" : COLUMN_GAP)))
                            .flatMap(spans -> spans);
                    return new TreeRow(
                            line(List.of(
                                            dim(prefix + connector(last, top)),
                                            dim(padLeft(String.valueOf(at), digits)),
                                            Span.raw(GAP))
                                    .appendAll(row)),
                            Option.of(TreeFolds.child(pointer, String.valueOf(at))),
                            range(element.line(), element.end()));
                })
                .prepend(keys);
    }

    /**
     * The rows of the sequence of leaves {@code sequence}, as many of its values on each as fit in the width, each
     * named by the indices it holds.
     */
    private List<TreeRow> packed(Tree.Sequence sequence, String pointer, String prefix, boolean top) {
        Vector<Tree> values = sequence.elements().toVector();
        int digits = String.valueOf(values.size() - 1).length();
        int label = 2 * digits + 1;
        int room = Math.max(16, width - prefix.length() - GUIDES.branch().length() - label - GAP.length());
        // In one pass, carrying how wide the row being filled is: a sequence of thousands is packed in time linear
        // in it.
        Packing done = Vector.range(0, values.size())
                .foldLeft(new Packing(Vector.empty(), Vector.empty(), 0), (packing, at) -> {
                    int needs = TextWidth.of(text(values.get(at))) + 2;
                    return !packing.filling().isEmpty() && packing.used() + needs > room
                            ? new Packing(packing.rows().append(packing.filling()), Vector.of(at), needs)
                            : new Packing(packing.rows(), packing.filling().append(at), packing.used() + needs);
                });
        Vector<Vector<Integer>> packed =
                done.filling().isEmpty() ? done.rows() : done.rows().append(done.filling());
        return packed.zipWithIndex((row, at) -> {
                    boolean last = at == packed.size() - 1;
                    String indices = row.size() == 1 ? "" + row.head() : row.head() + "–" + row.last();
                    List<Span> shown = row.toList()
                            .zipWithIndex((index, column) -> List.of(column == 0 ? Span.raw("") : dim(", "))
                                    .appendAll(leaf(values.get(index))))
                            .flatMap(spans -> spans);
                    return new TreeRow(
                            line(List.of(dim(prefix + connector(last, top)), dim(pad(indices, label)), Span.raw(GAP))
                                    .appendAll(shown)),
                            Option.of(TreeFolds.child(pointer, String.valueOf(row.head()))),
                            row.map(index -> values.get(index).line())
                                    .distinct()
                                    .toList());
                })
                .toList();
    }

    /**
     * The spans of {@code tree}, a container of leaves, on one line.
     */
    private List<Span> oneLine(Tree tree) {
        boolean mapping = tree instanceof Tree.Mapping;
        List<List<Span>> parts = mapping
                ? ((Tree.Mapping) tree)
                        .fields()
                        .map(field -> List.of(Span.raw(field.key()), dim(": ")).appendAll(leaf(field.value())))
                : ((Tree.Sequence) tree).elements().map(this::leaf);
        List<Span> between = parts.zipWithIndex((part, at) -> at == 0 ? part : part.prepend(dim(", ")))
                .flatMap(spans -> spans);
        return between.prepend(dim(mapping ? "{" : "[")).append(dim(mapping ? "}" : "]"));
    }

    /**
     * The spans of a leaf: its tag and anchor dim, then a scalar in the colour of its kind, an empty one dim as
     * {@value TreeText#NULL}, or an alias dim.
     */
    private List<Span> leaf(Tree tree) {
        String properties = properties(tree).text();
        List<Span> before = properties.isEmpty() ? List.empty() : List.of(dim(properties));
        return before.append(
                switch (tree) {
                    case Tree.Scalar scalar when TreeFolds.empty(scalar) -> dim(NULL);
                    case Tree.Scalar scalar -> Span.styled(value(scalar), styles.of(syntax(scalar.kind())));
                    default -> dim(value(tree));
                });
    }

    private Span cell(Tree tree, int width) {
        String text = cellText(tree);
        return switch (tree) {
            case Tree.Scalar scalar when TreeFolds.empty(scalar) -> dim(pad(text, width));
            case Tree.Scalar scalar ->
                Span.styled(
                        scalar.kind() == Tree.Kind.NUMBER ? padLeft(text, width) : pad(text, width),
                        styles.of(syntax(scalar.kind())));
            default -> dim(pad(text, width));
        };
    }

    private Span dim(String text) {
        return Span.styled(text, styles.of(Theme.Classes.DIM));
    }

    /**
     * Values being packed into rows: the {@code rows} filled, the indices on the row being filled, and how many columns
     * they take.
     */
    private record Packing(Vector<Vector<Integer>> rows, Vector<Integer> filling, int used) {}
}
