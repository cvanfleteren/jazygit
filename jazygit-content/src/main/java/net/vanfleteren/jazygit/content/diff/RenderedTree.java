package net.vanfleteren.jazygit.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.tree.GuideStyle;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.json.NotJson;
import net.vanfleteren.jazygit.state.diff.Rendered;
import net.vanfleteren.jazygit.state.diff.Tree;
import net.vanfleteren.jazygit.state.diff.TreeFolds;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.xml.NotXml;
import net.vanfleteren.jazygit.yaml.NotYaml;

/**
 * A version of a JSON, a YAML or an XML file rendered (ADR 122, 123, 151): a tree of rows, its guides as tamboui draws
 * those of a tree, a container of scalars that fits on one line, a long sequence of scalars packed into as many values
 * a row as the width holds, a sequence of records as a table, a mapping in a sequence named by one of its fields, a
 * block of YAML as its lines, each document of a file of more than one as a node, an element of XML with its
 * attributes, its text, its comments and its records as a table, and each node that folds open or closed as the user
 * chose. Each row knows the lines of the source it stands for, and the node it is, which the cursor stops on.
 */
final class RenderedTree {

    private static final GuideStyle GUIDES = GuideStyle.UNICODE;
    private static final String GAP = "  ";
    private static final String COLUMN_GAP = "  ";
    private static final String NEWLINE = "↵";
    private static final String NULL = "null";
    private static final String BLOCK_INDENT = "    ";
    private static final int PREVIEW = 40;
    private static final String LINES = "≡";
    private static final String COMMENT_OPEN = "<!--";
    private static final String COMMENT_CLOSE = "-->";
    private static final String CDATA = "CDATA";

    private RenderedTree() {}

    /**
     * The rows of the {@code lines} of a file of {@code kind}, JSON, YAML or XML, packed to {@code width} columns, its
     * nodes open as {@code chose} says ({@link TreeFolds#open}), styled as {@code styles} says; or, when they cannot be
     * read, or are {@code cut} short, the line that says why, to show over them as source.
     */
    static Either<String, List<TreeRow>> rows(
            Rendered kind,
            Vector<String> lines,
            boolean cut,
            int width,
            Map<String, Boolean> chose,
            Theme.SpanStyles styles) {
        return read(kind, lines, cut, width, chose, styles).mapLeft(NotRendered::why);
    }

    /**
     * As {@link #rows}, but when they cannot be read, also where reading stopped, when that is known.
     */
    static Either<NotRendered, List<TreeRow>> read(
            Rendered kind,
            Vector<String> lines,
            boolean cut,
            int width,
            Map<String, Boolean> chose,
            Theme.SpanStyles styles) {
        if (cut) {
            return Either.left(new NotRendered(
                    Messages.get(
                            switch (kind) {
                                case YAML -> "content.yaml.cut";
                                case XML -> "content.xml.cut";
                                default -> "content.json.cut";
                            }),
                    Option.none()));
        }
        return Tree.read(kind, lines.mkString("\n"))
                .mapLeft(unread -> new NotRendered(why(unread), place(unread)))
                .map(documents -> new Drawing(width, chose, styles).documents(documents));
    }

    /**
     * Where reading a file stopped, when it says so.
     */
    static Option<Place> place(Tree.Unread unread) {
        return switch (unread) {
            case Tree.Unread.NoJson(NotJson.Broken(int line, int column, var found)) ->
                Option.of(new Place(line, column));
            case Tree.Unread.NoJson(NotJson.MoreAfter(int line)) -> Option.of(new Place(line, 1));
            case Tree.Unread.NoYaml(NotYaml.Broken(int line, int column, var problem)) ->
                Option.of(new Place(line, column));
            case Tree.Unread.NoXml(NotXml.Broken(int line, int column, var problem)) ->
                Option.of(new Place(line, column));
            default -> Option.none();
        };
    }

    /**
     * What the line over a file that could not be read says.
     */
    static String why(Tree.Unread unread) {
        return switch (unread) {
            case Tree.Unread.NoJson(NotJson not) -> why(not);
            case Tree.Unread.NoYaml(NotYaml not) -> why(not);
            case Tree.Unread.NoXml(NotXml not) -> why(not);
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
     * What the line over XML that is not well formed says: where reading stopped, and why, in jazygit's words
     * (ADR 151).
     */
    static String why(NotXml not) {
        return switch (not) {
            case NotXml.Nothing nothing -> Messages.get("content.xml.nothing");
            case NotXml.TooDeep(int depth) -> Messages.get("content.xml.tooDeep", String.valueOf(depth));
            case NotXml.Broken(int line, int column, NotXml.Problem problem) ->
                Messages.get("content.xml.broken", String.valueOf(line), String.valueOf(column), problem(problem));
        };
    }

    private static String problem(NotXml.Problem problem) {
        return switch (problem) {
            case NotXml.Problem.Expected(String what)
            when what.equals(NotXml.Problem.NAME) -> Messages.get("content.xml.expectedName");
            case NotXml.Problem.Expected(String what)
            when what.equals(NotXml.Problem.QUOTED) -> Messages.get("content.xml.expectedQuoted");
            case NotXml.Problem.Expected(String what) -> Messages.get("content.xml.expected", what);
            case NotXml.Problem.Closes(String open, String close) -> Messages.get("content.xml.closes", close, open);
            case NotXml.Problem.ClosesNothing(String close) -> Messages.get("content.xml.closesNothing", close);
            case NotXml.Problem.EndsInside(String name) -> Messages.get("content.xml.endsInside", name);
            case NotXml.Problem.Twice(String name) -> Messages.get("content.xml.twice", name);
            case NotXml.Problem.MoreAfter more -> Messages.get("content.xml.moreAfter");
            case NotXml.Problem.TextOutside outside -> Messages.get("content.xml.textOutside");
            case NotXml.Problem.LessThan less -> Messages.get("content.xml.lessThan");
            case NotXml.Problem.Ampersand ampersand -> Messages.get("content.xml.ampersand");
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
            default -> Tree.Properties.NONE;
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
            boolean more = TextWidth.of(first) > PREVIEW || block.lines().size() > 1;
            return TextWidth.start(first, PREVIEW) + (more ? " …" : "");
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
     * What a closed element of XML shows of what it holds: the names of its elements, or when they are all of one name,
     * those of what the first holds, or the texts they hold; its first line where it holds text alone.
     */
    private static String preview(Tree.Element element) {
        Option<Tree.Text> text = TreeFolds.text(element);
        if (text.isDefined()) {
            return firstLine(text.get().lines());
        }
        List<Tree.Element> elements = element.children()
                .filter(child -> child instanceof Tree.Element)
                .map(Tree.Element.class::cast);
        if (elements.isEmpty()) {
            return element.children()
                    .find(child -> child instanceof Tree.Text)
                    .map(child -> firstLine(((Tree.Text) child).lines()))
                    .getOrElse("");
        }
        if (elements.map(Tree.Element::name).distinct().size() > 1) {
            return fitting(elements.map(Tree.Element::name));
        }
        List<String> inside = elements.head()
                .children()
                .filter(child -> child instanceof Tree.Element)
                .map(child -> ((Tree.Element) child).name());
        return inside.isEmpty()
                ? fitting(elements.map(each -> TreeFolds.text(each)
                        .map(only -> flat(only.lines().head()))
                        .getOrElse(each.name())))
                : "{" + fitting(inside) + "}";
    }

    /**
     * The first line of {@code lines} that holds anything, cut to {@value #PREVIEW} columns, and an ellipsis when that
     * is not all of them.
     */
    private static String firstLine(List<String> lines) {
        String first = lines.find(line -> !line.isBlank()).getOrElse("");
        boolean more = TextWidth.of(first) > PREVIEW || lines.size() > 1;
        return TextWidth.start(first, PREVIEW) + (more ? " …" : "");
    }

    /**
     * A comment of one line: {@code <!-- what it says -->}.
     */
    private static String oneComment(Tree.Comment comment) {
        String says = comment.lines().head();
        return says.isEmpty() ? COMMENT_OPEN + COMMENT_CLOSE : COMMENT_OPEN + " " + says + " " + COMMENT_CLOSE;
    }

    /**
     * As many of {@code parts} as fit in {@value #PREVIEW} columns, joined by commas, and an ellipsis when that is not
     * all of them.
     */
    private static String fitting(List<String> parts) {
        int shown = 0;
        int used = 0;
        for (String part : parts) {
            used += TextWidth.of(part) + 2;
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

    /**
     * {@code text} with spaces after it to take {@code width} columns: a character of two columns, as 東, counts as
     * two.
     */
    private static String pad(String text, int width) {
        return text + " ".repeat(Math.max(0, width - TextWidth.of(text)));
    }

    private static String padLeft(String text, int width) {
        return TextWidth.alignRight(text, width);
    }

    /**
     * Why a version is not rendered, and where in its source reading stopped, when that is known.
     *
     * @param why what the line over its source says
     */
    record NotRendered(String why, Option<Place> at) {}

    /**
     * A place in the source of a version.
     *
     * @param line   counted from 1
     * @param column counted from 1
     */
    record Place(int line, int column) {}

    /**
     * A row of a version of JSON, YAML or XML rendered.
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
            if (root instanceof Tree.Markup markup) {
                return TreeFolds.xpaths(markup.nodes(), "").flatMap(node -> xml(node, "", ""));
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
                return List.of(new TreeRow(
                        line(head.appendAll(after)), Option.of(pointer), range(element.line(), element.end())));
            }
            boolean open = TreeFolds.open(chose, pointer, element);
            int size = TreeFolds.size(element);
            String count =
                    text.isDefined() ? LINES + size : TreeFolds.alike(element) ? "[" + size + "]" : "{" + size + "}";
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
                                line(List.of(dim(inner + BLOCK_INDENT), Span.raw(written))),
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
         * The spans of the line {@code at} of {@code text}: the first after a dim mark when it is a section of CDATA.
         */
        private List<Span> textSpans(Tree.Text text, int at) {
            Span written = Span.raw(text.lines().get(at));
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
                                    dim(COMMENT_OPEN + " " + LINES
                                            + comment.lines().size()))
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
            Vector<Vector<String>> cells = records.map(
                            record -> columns.map(column -> flat(TreeFolds.cell(record, column)))
                                    .toVector())
                    .toVector();
            List<Integer> widths =
                    columns.zipWithIndex((name, column) -> cells.map(row -> TextWidth.of(row.get(column)))
                            .append(TextWidth.of(name))
                            .max()
                            .getOrElse(0));
            int digits = String.valueOf(records.size()).length();
            int lastColumn = columns.size() - 1;
            TreeRow names = new TreeRow(
                    line(List.of(dim(prefix + GUIDES.vertical()), Span.raw(" ".repeat(digits) + GAP))
                            .appendAll(columns.zipWithIndex((name, column) -> Span.raw(
                                    column == lastColumn ? name : pad(name, widths.get(column)) + COLUMN_GAP)))),
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
            return column.startsWith("@")
                    ? Span.styled(shown, styles.of(Theme.Classes.SYNTAX_STRING))
                    : Span.raw(shown);
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
