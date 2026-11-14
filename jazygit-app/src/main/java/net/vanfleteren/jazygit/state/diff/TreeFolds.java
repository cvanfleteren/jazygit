package net.vanfleteren.jazygit.state.diff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * How a version of JSON or YAML rendered is laid out, as far as folding it needs (ADR 122, 123): which of its mappings
 * and sequences are drawn on a line of their own, which are a table or packed, which blocks it has, and so which are
 * nodes that fold, each named by its path from the root as a JSON Pointer, and whether it is open.
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
     * Whether {@code tree} is a mapping or a sequence.
     */
    public static boolean container(Tree tree) {
        return tree instanceof Tree.Mapping || tree instanceof Tree.Sequence;
    }

    /**
     * Whether {@code tree} is drawn as one value: a scalar or an alias.
     */
    public static boolean leaf(Tree tree) {
        return tree instanceof Tree.Scalar || tree instanceof Tree.Alias;
    }

    /**
     * How many fields or elements {@code tree} has, or lines a block; none for a leaf.
     */
    public static int size(Tree tree) {
        return switch (tree) {
            case Tree.Mapping mapping -> mapping.fields().size();
            case Tree.Sequence sequence -> sequence.elements().size();
            case Tree.Block block -> block.lines().size();
            case Tree.Scalar scalar -> 0;
            case Tree.Alias alias -> 0;
        };
    }

    /**
     * The values {@code tree} holds, in order.
     */
    public static List<Tree> values(Tree tree) {
        return switch (tree) {
            case Tree.Mapping mapping -> mapping.fields().map(Tree.Field::value);
            case Tree.Sequence sequence -> sequence.elements();
            default -> List.empty();
        };
    }

    /**
     * Whether {@code tree} is drawn on one line: a container of leaves, empty or no wider than {@link #ONE_LINE}.
     */
    public static boolean oneLine(Tree tree) {
        if (!container(tree) || !values(tree).forAll(TreeFolds::leaf)) {
            return false;
        }
        int keys = tree instanceof Tree.Mapping mapping
                ? mapping.fields().map(field -> field.key().length() + 2).sum().intValue()
                : 0;
        int widths = values(tree).map(value -> width(value) + 2).sum().intValue();
        return keys + widths <= ONE_LINE;
    }

    /**
     * How many columns the leaf {@code tree} is drawn in: its tag and anchor, a quoted string with its quotes, an empty
     * value as {@code null}, each newline one column.
     */
    public static int width(Tree tree) {
        return switch (tree) {
            case Tree.Scalar scalar ->
                scalar.properties().text().length()
                        + (scalar.quote() == Tree.Quote.NONE ? 0 : 2)
                        + (empty(scalar) ? "null".length() : scalar.text().length());
            case Tree.Alias alias -> alias.anchor().length() + 1;
            default -> 0;
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
                                && mapping.fields().forAll(field -> leaf(field.value()))
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
                && sequence.elements().forAll(TreeFolds::leaf);
    }

    /**
     * Whether {@code tree} is a node that folds: a mapping or a sequence drawn on a row of its own, or a block.
     */
    public static boolean folds(Tree tree) {
        return (container(tree) && !oneLine(tree)) || tree instanceof Tree.Block;
    }

    /**
     * Whether the node {@code tree} at {@code pointer} is open: as the user {@code chose}, else open when it has no
     * more than {@link #STARTS_OPEN} entries.
     */
    public static boolean open(Map<String, Boolean> chose, String pointer, Tree tree) {
        return chose.get(pointer).getOrElse(() -> size(tree) <= STARTS_OPEN);
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
        return container(document.root()) && size(document.root()) > 0;
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
        return table(tree).isDefined() || packed(tree) || tree instanceof Tree.Block
                ? own
                : children(tree, pointer).foldLeft(own, (done, child) -> done.merge(nodes(child._2, child._1)));
    }

    private static List<Tuple2<String, Tree>> children(Tree tree, String pointer) {
        return switch (tree) {
            case Tree.Mapping mapping ->
                mapping.fields().map(field -> Tuple.of(child(pointer, field.key()), field.value()));
            case Tree.Sequence sequence ->
                sequence.elements().zipWithIndex((element, index) -> Tuple.of(child(pointer, "" + index), element));
            default -> List.empty();
        };
    }
}
