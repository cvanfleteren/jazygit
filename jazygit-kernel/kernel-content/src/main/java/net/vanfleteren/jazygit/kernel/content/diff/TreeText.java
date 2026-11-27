package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.widgets.tree.GuideStyle;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.diff.Tree;
import net.vanfleteren.jazygit.kernel.state.diff.TreeFolds;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * What the rows of a version rendered write of the nodes of a tree, apart from how they are coloured: the guides, the
 * counts and previews of closed nodes, leaves as they are written, and padding to a width (ADR 122, 123, 151).
 */
final class TreeText {

    static final GuideStyle GUIDES = GuideStyle.UNICODE;
    static final String GAP = "  ";
    static final String COLUMN_GAP = "  ";
    static final String NEWLINE = "↵";
    static final String NULL = "null";
    static final String BLOCK_INDENT = "    ";
    static final int PREVIEW = 40;
    static final String LINES = "≡";
    static final String COMMENT_OPEN = "<!--";
    static final String COMMENT_CLOSE = "-->";
    static final String CDATA = "CDATA";

    private TreeText() {}

    static List<Entry> entries(Tree tree, String pointer) {
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

    static Tree.Mapping without(Tree.Mapping mapping, Tree.Field field) {
        return new Tree.Mapping(
                mapping.line(),
                mapping.end(),
                mapping.properties(),
                mapping.fields().remove(field));
    }

    static Tree.Properties properties(Tree tree) {
        return switch (tree) {
            case Tree.Mapping mapping -> mapping.properties();
            case Tree.Sequence sequence -> sequence.properties();
            case Tree.Scalar scalar -> scalar.properties();
            case Tree.Block block -> block.properties();
            default -> Tree.Properties.NONE;
        };
    }

    static String connector(boolean last, boolean top) {
        return top ? "" : last ? GUIDES.lastBranch() : GUIDES.branch();
    }

    static String continued(boolean last, boolean top) {
        return top ? "" : last ? GUIDES.space() : GUIDES.vertical();
    }

    static String count(Tree tree) {
        int size = TreeFolds.size(tree);
        return switch (tree) {
            case Tree.Mapping mapping -> "{" + size + "}";
            case Tree.Block block -> (block.folded() ? ">" : "|") + size;
            default -> "[" + size + "]";
        };
    }

    /**
     * What a closed node shows of what it holds: the keys of a mapping, the keys of the mappings of a sequence, the
     * first values of a sequence of leaves, as many as fit in {@value PREVIEW} columns, or the first line of a block
     * that holds any.
     */
    static String preview(Tree tree) {
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
                .takeWhile(Tree.Leaf.class::isInstance)
                .map(TreeText::text)
                .appendAll(
                        sequence.elements().exists(element -> !(element instanceof Tree.Leaf))
                                ? List.of("…")
                                : List.empty()));
    }

    /**
     * What a closed element of XML shows of what it holds: the names of its elements, or when they are all of one name,
     * those of what the first holds, or the texts they hold; its first line where it holds text alone.
     */
    static String preview(Tree.Element element) {
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
     * The first line of {@code lines} that holds anything, cut to {@value PREVIEW} columns, and an ellipsis when that
     * is not all of them.
     */
    static String firstLine(List<String> lines) {
        String first = flat(lines.find(line -> !line.isBlank()).getOrElse(""));
        boolean more = TextWidth.of(first) > PREVIEW || lines.size() > 1;
        return TextWidth.start(first, PREVIEW) + (more ? " …" : "");
    }

    /**
     * A comment of one line: {@code <!-- what it says -->}.
     */
    static String oneComment(Tree.Comment comment) {
        String says = comment.lines().head();
        return says.isEmpty() ? COMMENT_OPEN + COMMENT_CLOSE : COMMENT_OPEN + " " + says + " " + COMMENT_CLOSE;
    }

    /**
     * As many of {@code parts} as fit in {@value PREVIEW} columns, joined by commas, and an ellipsis when that is not
     * all of them.
     */
    static String fitting(List<String> parts) {
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
    static String text(Tree tree) {
        return properties(tree).text() + value(tree);
    }

    static String value(Tree tree) {
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
    static String cellText(Tree tree) {
        return tree instanceof Tree.Scalar scalar && scalar.quote() != Tree.Quote.NONE
                ? scalar.properties().text() + flat(scalar.text())
                : text(tree);
    }

    static String flat(String text) {
        return text.replace("\r\n", NEWLINE).replace("\n", NEWLINE).replace("\r", NEWLINE);
    }

    static String syntax(Tree.Kind kind) {
        return switch (kind) {
            case STRING -> Theme.Classes.SYNTAX_STRING;
            case NUMBER -> Theme.Classes.SYNTAX_NUMBER;
            case BOOLEAN, NULL -> Theme.Classes.SYNTAX_KEYWORD;
        };
    }

    static List<Integer> range(int from, int to) {
        return List.rangeClosed(from, Math.max(from, to));
    }

    /**
     * {@code text} with spaces after it to take {@code width} columns: a character of two columns, as 東, counts as
     * two.
     */
    static String pad(String text, int width) {
        return text + " ".repeat(Math.max(0, width - TextWidth.of(text)));
    }

    static String padLeft(String text, int width) {
        return TextWidth.alignRight(text, width);
    }

    /**
     * A line of {@code spans}, without the empty ones.
     */
    static Line line(List<Span> spans) {
        return Line.from(spans.filter(span -> !span.isEmpty()).toJavaList());
    }

    /**
     * What a node holds, as the rows after it show it: a field with its key, an element without one.
     */
    record Entry(Option<String> key, String pointer, int line, Tree value, List<Integer> ignored) {}
}
