package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * How a version of JSON, YAML or XML rendered is laid out, as far as folding it needs (ADR 122, 123, 151): which of its
 * mappings and sequences are drawn on a line of their own, which are a table or packed, which blocks it has, which
 * elements and comments of XML hold more than a row, and so which are nodes that fold, each named by its path from the
 * root, as a JSON Pointer, or for XML as XPath writes one, and whether it is open.
 */
public final class TreeFolds {

    /**
     * The widest a container of scalars is drawn on one line, its brackets, keys and values with the commas and blanks
     * between them.
     */
    public static final int ONE_LINE = 48;

    /**
     * The most entries a node has that starts open: fields, elements, or the lines of a block.
     */
    public static final int STARTS_OPEN = 20;

    /**
     * The fields that name an object in an array, the first it has of them.
     */
    public static final List<String> NAMING = List.of("name", "title", "id", "login", "key", "path");

    /**
     * The fields that name a document of a file of more than one, the first it has of them: {@code kind} first, as
     * Kubernetes has it, else every manifest is named by its {@code apiVersion} (ADR 123).
     */
    public static final List<String> DOCUMENT_NAMING = NAMING.prepend("kind");

    private TreeFolds() {}

    /**
     * How many fields or elements {@code tree} has, or lines a block; none for a leaf.
     */
    public static int size(Tree tree) {
        return switch (tree) {
            case Tree.Mapping mapping -> mapping.fields().size();
            case Tree.Sequence sequence -> sequence.elements().size();
            case Tree.Block block -> block.lines().size();
            case Tree.Leaf _ -> 0;
            case Tree.Markup markup -> markup.nodes().size();
            case Tree.Element element ->
                text(element)
                        .map(text -> text.lines().size())
                        .getOrElse(() -> element.children().size());
            case Tree.Text text -> text.lines().size();
            case Tree.Comment comment -> comment.lines().size();
        };
    }

    /**
     * The values {@code tree} holds, in order.
     */
    public static List<Tree> values(Tree tree) {
        return switch (tree) {
            case Tree.Mapping mapping -> mapping.fields().map(Tree.Field::value);
            case Tree.Sequence sequence -> sequence.elements();
            case Tree.Scalar _, Tree.Block _, Tree.Alias _ -> List.empty();
            case Tree.Markup _, Tree.Element _, Tree.Text _, Tree.Comment _ -> List.empty();
        };
    }

    /**
     * Whether {@code tree} is drawn on one line: a container of leaves, empty or no wider than {@link #ONE_LINE}.
     */
    public static boolean oneLine(Tree tree) {
        if (!(tree instanceof Tree.Container) || !values(tree).forAll(Tree.Leaf.class::isInstance)) {
            return false;
        }
        int keys = tree instanceof Tree.Mapping mapping
                ? mapping.fields().map(field -> field.key().length() + 2).sum().intValue()
                : 0;
        int widths = values(tree).map(value -> width(value) + 2).sum().intValue();
        return keys + widths <= ONE_LINE;
    }

    /**
     * How many characters the leaf {@code tree} is drawn with, for {@link #oneLine} only: its tag and anchor, a quoted
     * string with its quotes, an empty value as {@code null}, each newline one. Characters, not columns: the model knows
     * nothing of the terminal, so a character two columns wide counts one here. What is drawn is measured in columns
     * where it is drawn.
     */
    private static int width(Tree tree) {
        return switch (tree) {
            case Tree.Scalar scalar ->
                scalar.properties().text().length()
                        + (scalar.quote() == Tree.Quote.NONE ? 0 : 2)
                        + (empty(scalar) ? "null".length() : scalar.text().length());
            case Tree.Alias alias -> alias.anchor().length() + 1;
            case Tree.Mapping _, Tree.Sequence _, Tree.Block _ -> 0;
            case Tree.Markup _, Tree.Element _, Tree.Text _, Tree.Comment _ -> 0;
        };
    }

    /**
     * Whether {@code scalar} is a value of YAML written as nothing, drawn as {@code null}.
     */
    public static boolean empty(Tree.Scalar scalar) {
        return scalar.kind() == Tree.Kind.NULL && scalar.text().isEmpty();
    }

    /**
     * The keys of {@code tree} as the columns of a table, if it is one: a sequence of two or more mappings that hold
     * only leaves, all with the same keys in the same order.
     */
    public static Option<List<String>> table(Tree tree) {
        if (!(tree instanceof Tree.Sequence sequence) || sequence.elements().size() < 2) {
            return Option.none();
        }
        List<Option<List<String>>> keys = sequence.elements()
                .map(element -> element instanceof Tree.Mapping mapping
                                && !mapping.fields().isEmpty()
                                && mapping.fields().forAll(field -> field.value() instanceof Tree.Leaf)
                        ? Option.of(mapping.fields().map(Tree.Field::key))
                        : Option.none());
        return keys.head().filter(first -> keys.forAll(other -> other.contains(first)));
    }

    /**
     * Whether {@code tree} is packed: a sequence of more than {@link #STARTS_OPEN} leaves, drawn as many on a row as
     * fit.
     */
    public static boolean packed(Tree tree) {
        return tree instanceof Tree.Sequence sequence
                && sequence.elements().size() > STARTS_OPEN
                && sequence.elements().forAll(Tree.Leaf.class::isInstance);
    }

    /**
     * Whether {@code tree} is a node that folds: a mapping or a sequence drawn on a row of its own, or a block.
     */
    public static boolean folds(Tree tree) {
        return switch (tree) {
            case Tree.Element element -> !oneRow(element);
            case Tree.Comment comment -> comment.lines().size() > 1;
            case Tree.Mapping _, Tree.Sequence _ -> !oneLine(tree);
            case Tree.Block _ -> true;
            case Tree.Scalar _, Tree.Alias _, Tree.Markup _, Tree.Text _ -> false;
        };
    }

    /**
     * Whether the node {@code tree} at {@code pointer} is open: as the user {@code chose}, else open when it is the root
     * element of XML, or has no more than {@link #STARTS_OPEN} entries.
     */
    public static boolean open(Map<String, Boolean> chose, String pointer, Tree tree) {
        return chose.get(pointer).getOrElse(() -> root(pointer, tree) || size(tree) <= STARTS_OPEN);
    }

    /**
     * Whether the node {@code tree} at {@code pointer} is the root element of XML, which {@code z} leaves as it is
     * (ADR 151).
     */
    public static boolean root(String pointer, Tree tree) {
        return tree instanceof Tree.Element && pointer.lastIndexOf('/') == 0;
    }

    /**
     * The text {@code element} holds, when it holds that alone.
     */
    public static Option<Tree.Text> text(Tree.Element element) {
        return element.children().size() == 1 && element.children().head() instanceof Tree.Text text
                ? Option.of(text)
                : Option.none();
    }

    /**
     * Whether {@code element} is drawn on one row: it holds nothing, or text on one line alone.
     */
    public static boolean oneRow(Tree.Element element) {
        return element.children().isEmpty()
                || text(element).exists(text -> text.lines().size() == 1);
    }

    /**
     * Whether all {@code element} holds are elements of one name.
     */
    public static boolean alike(Tree.Element element) {
        List<Tree> children = element.children();
        return !children.isEmpty()
                && children.forAll(child -> child instanceof Tree.Element)
                && children.map(child -> ((Tree.Element) child).name())
                                .distinct()
                                .size()
                        == 1;
    }

    /**
     * The columns of {@code element} as a table, if it is one (ADR 151): it holds two or more elements of one name, and
     * else only comments of one line, each holding only attributes and elements of text on one line or of none, of names
     * it has once, with no attributes. A column for each attribute, {@code @name}, and each element, in the order they
     * are first seen.
     */
    public static Option<List<String>> columns(Tree.Element element) {
        List<Tree.Element> records = element.children()
                .filter(child -> child instanceof Tree.Element)
                .map(Tree.Element.class::cast);
        boolean others = element.children()
                .forAll(child -> child instanceof Tree.Element
                        || child instanceof Tree.Comment comment
                                && comment.lines().size() == 1);
        if (records.size() < 2
                || !others
                || records.map(Tree.Element::name).distinct().size() > 1) {
            return Option.none();
        }
        Option<List<List<String>>> keys =
                Option.sequence(records.map(TreeFolds::record)).map(List::ofAll);
        return keys.map(each -> each.flatMap(names -> names).distinct()).filter(columns -> !columns.isEmpty());
    }

    /**
     * The keys of {@code element} as a row of a table: its attributes as {@code @name}, then its elements, if it is one.
     */
    private static Option<List<String>> record(Tree.Element element) {
        List<Option<String>> cells = element.children()
                .map(child ->
                        child instanceof Tree.Element cell && cell.attributes().isEmpty() && oneRow(cell)
                                ? Option.of(cell.name())
                                : Option.<String>none());
        if (cells.exists(Option::isEmpty)) {
            return Option.none();
        }
        List<String> names = cells.map(Option::get);
        return Option.when(
                names.distinct().size() == names.size(),
                () -> element.attributes()
                        .map(attribute -> "@" + attribute.name())
                        .appendAll(names));
    }

    /**
     * The text of the cell {@code column} of {@code element}, a row of a table; empty where it has none.
     */
    public static String cell(Tree.Element element, String column) {
        if (column.startsWith("@")) {
            return element.attributes()
                    .find(attribute -> attribute.name().equals(column.substring(1)))
                    .map(Tree.Attribute::value)
                    .getOrElse("");
        }
        return element.children()
                .find(child -> child instanceof Tree.Element cell && cell.name().equals(column))
                .flatMap(child -> text((Tree.Element) child))
                .map(text -> text.lines().head())
                .getOrElse("");
    }

    /**
     * Each of {@code nodes}, what an element or the top of XML holds, with its path under {@code pointer}, as XPath
     * writes one: an element by its name, a text as {@code text()}, a comment as {@code comment()}, each with an index
     * from 1 where it has siblings named the same.
     */
    public static List<Placed> xpaths(List<Tree> nodes, String pointer) {
        Map<String, Integer> counts =
                nodes.map(TreeFolds::xpathName).groupBy(name -> name).mapValues(List::size);
        // In one pass, carrying how many of each name came before: an element of thousands is named in linear time.
        return nodes.foldLeft(Tuple.of(List.<Placed>empty(), HashMap.<String, Integer>empty()), (done, node) -> {
                    String name = xpathName(node);
                    int index = done._2.get(name).getOrElse(0) + 1;
                    String step = counts.get(name).get() > 1 ? name + "[" + index + "]" : name;
                    return Tuple.of(done._1.prepend(new Placed(child(pointer, step), node)), done._2.put(name, index));
                })
                ._1
                .reverse();
    }

    private static String xpathName(Tree node) {
        return switch (node) {
            case Tree.Element element -> element.name();
            case Tree.Text text -> "text()";
            case Tree.Comment comment -> "comment()";
            default -> "node()";
        };
    }

    /**
     * The field of the mapping {@code tree} that names it, if it has one: the first it has of {@code naming} with a
     * scalar value, else its first scalar.
     */
    public static Option<Tree.Field> naming(Tree.Mapping tree, List<String> naming) {
        List<Tree.Field> scalars = tree.fields().filter(field -> field.value() instanceof Tree.Scalar);
        return naming.flatMap(key -> scalars.find(field -> field.key().equals(key)))
                .headOption()
                .orElse(scalars::headOption);
    }

    /**
     * The path of the field {@code key}, or the element at index {@code key}, of what is at {@code pointer}: its
     * {@code ~} written {@code ~0} and its {@code /} written {@code ~1}, as a JSON Pointer has them.
     */
    public static String child(String pointer, String key) {
        return pointer + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Whether a document of {@code documents} is drawn as a node of its own: when there are more than one, each is,
     * by its index; one is drawn as the root.
     */
    public static boolean apart(List<Tree.Document> documents) {
        return documents.size() > 1;
    }

    /**
     * Whether the root of a document drawn apart folds: a mapping or a sequence that holds anything.
     */
    public static boolean foldsApart(Tree.Document document) {
        return document.root() instanceof Tree.Container && size(document.root()) > 0;
    }

    /**
     * Every node of {@code documents} that folds, by its path, in the order they are drawn: of one document not its
     * root, which is drawn open always, nor what a table, a packed sequence or a block holds; of more than one, each
     * document whose root folds too.
     */
    public static Map<String, Tree> nodes(List<Tree.Document> documents) {
        if (!apart(documents)) {
            return children(documents.head().root(), "")
                    .foldLeft(LinkedHashMap.empty(), (done, child) -> done.merge(nodes(child._2, child._1)));
        }
        return documents.zipWithIndex().foldLeft(LinkedHashMap.empty(), (done, at) -> {
            String pointer = child("", String.valueOf(at._2));
            Tree root = at._1.root();
            return foldsApart(at._1)
                    ? children(root, pointer)
                            .foldLeft(done.put(pointer, root), (inner, child) -> inner.merge(nodes(child._2, child._1)))
                    : done;
        });
    }

    private static Map<String, Tree> nodes(Tree tree, String pointer) {
        if (!folds(tree)) {
            return LinkedHashMap.empty();
        }
        Map<String, Tree> own = LinkedHashMap.of(pointer, tree);
        boolean whole = tree instanceof Tree.Element element
                && (text(element).isDefined() || columns(element).isDefined());
        boolean foldsWhole = table(tree).isDefined()
                || packed(tree)
                || tree instanceof Tree.Block
                || tree instanceof Tree.Comment
                || whole;
        return foldsWhole
                ? own
                : children(tree, pointer).foldLeft(own, (done, child) -> done.merge(nodes(child._2, child._1)));
    }

    private static List<Tuple2<String, Tree>> children(Tree tree, String pointer) {
        return switch (tree) {
            case Tree.Mapping mapping ->
                mapping.fields().map(field -> Tuple.of(child(pointer, field.key()), field.value()));
            case Tree.Sequence sequence ->
                sequence.elements().zipWithIndex((element, index) -> Tuple.of(child(pointer, "" + index), element));
            case Tree.Markup markup -> xpaths(markup.nodes(), pointer).map(Placed::pair);
            case Tree.Element element -> xpaths(element.children(), pointer).map(Placed::pair);
            case Tree.Scalar _, Tree.Block _, Tree.Alias _, Tree.Text _, Tree.Comment _ -> List.empty();
        };
    }

    /**
     * A node of XML and its path.
     */
    public record Placed(String pointer, Tree node) {

        private Tuple2<String, Tree> pair() {
            return Tuple.of(pointer, node);
        }
    }
}
