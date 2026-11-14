package net.vanfleteren.jazygit.ui.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.tree.GuideStyle;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.json.NotJson;
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.state.diff.Tree;
import net.vanfleteren.jazygit.state.diff.TreeFolds;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.yaml.NotYaml;

/**
 * A version of a JSON or a YAML file rendered (ADR 122, 123): a tree of rows, its guides as tamboui draws those of a
 * tree, a container of scalars that fits on one line, a long sequence of scalars packed into as many values a row as
 * the width holds, a sequence of records as a table, a mapping in a sequence named by one of its fields, a block of
 * YAML as its lines, each document of a file of more than one as a node, and each node that folds open or closed as
 * the user chose. Each row knows the lines of the source it stands for, and the node it is, which the cursor stops on.
 */
final class RenderedTree {

    private static final GuideStyle GUIDES = GuideStyle.UNICODE;
    private static final String GAP = "  ";
    private static final String COLUMN_GAP = "  ";
    private static final String NEWLINE = "↵";
    private static final String NULL = "null";
    private static final String BLOCK_INDENT = "    ";
    private static final int PREVIEW = 40;

    private RenderedTree() {}

    /**
     * The rows of the {@code lines} of a file of {@code kind}, JSON or YAML, packed to {@code width} columns, its nodes
     * open as {@code chose} says ({@link TreeFolds#open}), styled as {@code styles} says; or, when they cannot be read,
     * or are {@code cut} short, the line that says why, to show over them as source.
     */
    static Either<String, List<TreeRow>> rows(
            Rendered kind,
            Vector<String> lines,
            boolean cut,
            int width,
            Map<String, Boolean> chose,
            Theme.SpanStyles styles) {
        if (cut) {
            return Either.left(Messages.get(kind == Rendered.YAML ? "content.yaml.cut" : "content.json.cut"));
        }
        return Tree.read(kind, lines.mkString("\n"))
                .mapLeft(RenderedTree::why)
                .map(documents -> new Drawing(width, chose, styles).documents(documents));
    }

    /**
     * What the line over a file that could not be read says.
     */
    static String why(Tree.Unread unread) {
        return switch (unread) {
            case Tree.Unread.NoJson(NotJson not) -> why(not);
            case Tree.Unread.NoYaml(NotYaml not) -> why(not);
            case Tree.Unread.NoTree none -> Messages.get("content.json.nothing");
        };
    }

    /**
     * What the line over JSON that could not be read says.
     */
    static String why(NotJson not) {
        return switch (not) {
            case NotJson.Nothing nothing -> Messages.get("content.json.nothing");
            case NotJson.MoreAfter(int line) -> Messages.get("content.json.moreAfter", String.valueOf(line));
            case NotJson.TooDeep(int depth) -> Messages.get("content.json.tooDeep", String.valueOf(depth));
            case NotJson.Broken(int line, int column, var found) ->
                Messages.get(
                        "content.json.unexpected",
                        String.valueOf(line),
                        String.valueOf(column),
                        found.map(RenderedTree::token).getOrElse(() -> Messages.get("content.json.end")));
        };
    }

    /**
     * What the line over YAML that could not be read says: where SnakeYAML stopped, and what it missed, in its words
     * (ADR 123).
     */
    static String why(NotYaml not) {
        return switch (not) {
            case NotYaml.Nothing nothing -> Messages.get("content.yaml.nothing");
            case NotYaml.TooDeep(int depth) -> Messages.get("content.yaml.tooDeep", String.valueOf(depth));
            case NotYaml.Broken(int line, int column, String problem) ->
                Messages.get("content.yaml.broken", String.valueOf(line), String.valueOf(column), problem);
        };
    }

    /**
     * How the line over JSON that could not be read names the {@code token} found: a string as one, a line break as
     * one, a character that draws nothing or draws badly, as a tab or a control character, by its code point, and
     * anything else in quotes as it is written.
     */
    private static String token(String token) {
        if (token.startsWith("\"")) {
            return Messages.get("content.json.string");
        }
        if (token.equals("\n")) {
            return Messages.get("content.json.lineBreak");
        }
        return token.codePoints().anyMatch(RenderedTree::unseen)
                ? Messages.get("content.json.character", String.format("U+%04X", token.codePointAt(0)))
                : "'" + token + "'";
    }

    /**
     * Whether {@code codePoint} draws nothing a reader sees, or draws badly in a terminal: white space, a space that
     * does not break, a control character or a format character, as a byte order mark.
     */
    private static boolean unseen(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }

    private static List<Entry> entries(Tree tree, String pointer) {
        return switch (tree) {
            case Tree.Mapping mapping ->
                mapping.fields()
                        .map(field -> new Entry(
                                Option.of(field.key()),
                                TreeFolds.child(pointer, field.key()),
                                field.line(),
                                field.value(),
                                field.ignored()));
            case Tree.Sequence sequence ->
                sequence.elements()
                        .zipWithIndex((element, at) -> new Entry(
                                Option.none(),
                                TreeFolds.child(pointer, String.valueOf(at)),
                                element.line(),
                                element,
                                List.empty()));
            default -> List.empty();
        };
    }

    private static Tree.Mapping without(Tree.Mapping mapping, Tree.Field field) {
        return new Tree.Mapping(
                mapping.line(),
                mapping.end(),
                mapping.properties(),
                mapping.fields().remove(field));
    }

    private static Tree.Properties properties(Tree tree) {
        return switch (tree) {
            case Tree.Mapping mapping -> mapping.properties();
            case Tree.Sequence sequence -> sequence.properties();
            case Tree.Scalar scalar -> scalar.properties();
            case Tree.Block block -> block.properties();
            case Tree.Alias alias -> Tree.Properties.NONE;
        };
    }

    private static String connector(boolean last, boolean top) {
        return top ? "" : last ? GUIDES.lastBranch() : GUIDES.branch();
    }

    private static String continued(boolean last, boolean top) {
        return top ? "" : last ? GUIDES.space() : GUIDES.vertical();
    }

    private static String count(Tree tree) {
        int size = TreeFolds.size(tree);
        return switch (tree) {
            case Tree.Mapping mapping -> "{" + size + "}";
            case Tree.Block block -> (block.folded() ? ">" : "|") + size;
            default -> "[" + size + "]";
        };
    }

    /**
     * What a closed node shows of what it holds: the keys of a mapping, the keys of the mappings of a sequence, the
     * first values of a sequence of leaves, as many as fit in {@value #PREVIEW} columns, or the first line of a block
     * that holds any.
     */
    private static String preview(Tree tree) {
        if (tree instanceof Tree.Mapping mapping) {
            return fitting(mapping.fields().map(Tree.Field::key));
        }
        if (tree instanceof Tree.Block block) {
            String first = block.lines().find(line -> !line.isBlank()).getOrElse("");
            boolean more = first.length() > PREVIEW || block.lines().size() > 1;
            return (first.length() > PREVIEW ? first.substring(0, PREVIEW) : first) + (more ? " …" : "");
        }
        Tree.Sequence sequence = (Tree.Sequence) tree;
        Option<Tree> first = sequence.elements().headOption();
        if (first.exists(element -> element instanceof Tree.Mapping)) {
            return "{" + fitting(((Tree.Mapping) first.get()).fields().map(Tree.Field::key)) + "}";
        }
        return fitting(sequence.elements()
                .takeWhile(TreeFolds::leaf)
                .map(RenderedTree::text)
                .appendAll(
                        sequence.elements().exists(element -> !TreeFolds.leaf(element)) ? List.of("…") : List.empty()));
    }

    /**
     * As many of {@code parts} as fit in {@value #PREVIEW} columns, joined by commas, and an ellipsis when that is not
     * all of them.
     */
    private static String fitting(List<String> parts) {
        int shown = 0;
        int used = 0;
        for (String part : parts) {
            used += part.length() + 2;
            if (used > PREVIEW) {
                break;
            }
            shown++;
        }
        List<String> fit = parts.take(shown);
        return shown < parts.size() ? fit.append("…").mkString(", ") : fit.mkString(", ");
    }

    /**
     * How a leaf is written: its tag and anchor, a scalar in the quotes it was written in, its newlines as
     * {@value #NEWLINE}, an empty one as {@value #NULL}, an alias with its {@code *}.
     */
    private static String text(Tree tree) {
        return properties(tree).text() + value(tree);
    }

    private static String value(Tree tree) {
        return switch (tree) {
            case Tree.Scalar scalar ->
                switch (scalar.quote()) {
                    case DOUBLE -> "\"" + flat(scalar.text()) + "\"";
                    case SINGLE -> "'" + flat(scalar.text()) + "'";
                    case NONE -> TreeFolds.empty(scalar) ? NULL : flat(scalar.text());
                };
            case Tree.Alias alias -> "*" + alias.anchor();
            default -> "";
        };
    }

    /**
     * How a leaf is written in a table: a string without its quotes.
     */
    private static String cellText(Tree tree) {
        return tree instanceof Tree.Scalar scalar && scalar.quote() != Tree.Quote.NONE
                ? scalar.properties().text() + flat(scalar.text())
                : text(tree);
    }

    private static String flat(String text) {
        return text.replace("\r\n", NEWLINE).replace("\n", NEWLINE).replace("\r", NEWLINE);
    }

    private static String syntax(Tree.Kind kind) {
        return switch (kind) {
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
            case BOOLEAN, NULL -> Theme.Classes.SYNTAX_KEYWORD;
        };
    }

    private static List<Integer> range(int from, int to) {
        return List.rangeClosed(from, Math.max(from, to));
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    /**
     * A row of a version of JSON or YAML rendered.
     *
     * @param line    what it draws
     * @param pointer the path of what it draws, which the cursor stops on; none for the first row of a table, which
     *                is part of the row before it
     * @param lines   the lines of the source it stands for, counted from 1, the one it starts on first
     */
    record TreeRow(Line line, Option<String> pointer, List<Integer> lines) {}

    /**
     * Draws the rows of a tree.
     */
    private record Drawing(int width, Map<String, Boolean> chose, Theme.SpanStyles styles) {

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
            Tree shown = naming.map(field -> (Tree) without((Tree.Mapping) root, field))
                    .getOrElse(root);
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
                Span label =
                        entry.key().map(key -> Span.raw(key)).getOrElse(() -> dim(pad(String.valueOf(at._2), digits)));
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
            Tree shown = naming.map(field -> (Tree) without((Tree.Mapping) value, field))
                    .getOrElse(value);
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
            return naming.map(field ->
                            List.of(Span.raw(GAP), dim(field.key() + ": ")).appendAll(leaf(field.value())))
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
                    .map(element -> ((Tree.Mapping) element)
                            .fields()
                            .map(Tree.Field::value)
                            .toVector())
                    .toVector();
            int size = cells.size();
            List<Integer> widths = columns.zipWithIndex(
                    (key, column) -> cells.map(row -> cellText(row.get(column)).length())
                            .append(key.length())
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
                        int needs = TreeFolds.width(values.get(at)) + 2;
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
                                line(List.of(
                                                dim(prefix + connector(last, top)),
                                                dim(pad(indices, label)),
                                                Span.raw(GAP))
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
                            .map(field ->
                                    List.of(Span.raw(field.key()), dim(": ")).appendAll(leaf(field.value())))
                    : ((Tree.Sequence) tree).elements().map(this::leaf);
            List<Span> between = parts.zipWithIndex((part, at) -> at == 0 ? part : part.prepend(dim(", ")))
                    .flatMap(spans -> spans);
            return between.prepend(dim(mapping ? "{" : "[")).append(dim(mapping ? "}" : "]"));
        }

        /**
         * The spans of a leaf: its tag and anchor dim, then a scalar in the colour of its kind, an empty one dim as
         * {@value #NULL}, or an alias dim.
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

        private static Line line(List<Span> spans) {
            return Line.from(spans.filter(span -> !span.isEmpty()).toJavaList());
        }
    }

    /**
     * Values being packed into rows: the {@code rows} filled, the indices on the row being filled, and how many columns
     * they take.
     */
    private record Packing(Vector<Vector<Integer>> rows, Vector<Integer> filling, int used) {}

    /**
     * What a node holds, as the rows after it show it: a field with its key, an element without one.
     */
    private record Entry(Option<String> key, String pointer, int line, Tree value, List<Integer> ignored) {}
}
