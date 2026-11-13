package net.vanfleteren.jazygit.state.diff;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.json.JsonTree;

/**
 * How a version of JSON rendered is laid out, as far as folding it needs (ADR 122): which of its objects and arrays are
 * drawn on a line of their own, which are a table or packed, and so which are nodes that fold, each named by its path
 * from the root as a JSON Pointer, and whether it is open.
 */
public final class JsonFolds {

    /**
     * The widest a container of scalars is drawn on one line, its brackets, keys and values with the commas and blanks
     * between them.
     */
    public static final int ONE_LINE = 48;

    /**
     * The most entries a node has that starts open.
     */
    public static final int STARTS_OPEN = 20;

    /**
     * The fields that name an object in an array, the first it has of them.
     */
    public static final List<String> NAMING = List.of("name", "title", "id", "login", "key", "path");

    private JsonFolds() {}

    /**
     * Whether {@code tree} is an object or an array.
     */
    public static boolean container(JsonTree tree) {
        return !(tree instanceof JsonTree.Scalar);
    }

    /**
     * How many fields or elements {@code tree} has; none for a scalar.
     */
    public static int size(JsonTree tree) {
        return switch (tree) {
            case JsonTree.JsonObject object -> object.fields().size();
            case JsonTree.JsonArray array -> array.elements().size();
            case JsonTree.Scalar scalar -> 0;
        };
    }

    /**
     * The values {@code tree} holds, in order.
     */
    public static List<JsonTree> values(JsonTree tree) {
        return switch (tree) {
            case JsonTree.JsonObject object -> object.fields().map(JsonTree.Field::value);
            case JsonTree.JsonArray array -> array.elements();
            case JsonTree.Scalar scalar -> List.empty();
        };
    }

    /**
     * Whether {@code tree} is drawn on one line: a container of scalars, empty or no wider than {@link #ONE_LINE}.
     */
    public static boolean oneLine(JsonTree tree) {
        if (!container(tree) || !values(tree).forAll(value -> !container(value))) {
            return false;
        }
        int keys = tree instanceof JsonTree.JsonObject object
                ? object.fields().map(field -> field.key().length() + 2).sum().intValue()
                : 0;
        int widths = values(tree)
                .map(value -> width((JsonTree.Scalar) value) + 2)
                .sum()
                .intValue();
        return keys + widths <= ONE_LINE;
    }

    /**
     * How many columns {@code scalar} is drawn in: a string with its quotes, each newline in it one column.
     */
    public static int width(JsonTree.Scalar scalar) {
        return scalar.kind() == JsonTree.Kind.STRING
                ? scalar.text().length() + 2
                : scalar.text().length();
    }

    /**
     * The keys of {@code tree} as the columns of a table, if it is one: an array of two or more objects that hold only
     * scalars, all with the same keys in the same order.
     */
    public static Option<List<String>> table(JsonTree tree) {
        if (!(tree instanceof JsonTree.JsonArray array) || array.elements().size() < 2) {
            return Option.none();
        }
        List<Option<List<String>>> keys = array.elements()
                .map(element -> element instanceof JsonTree.JsonObject object
                                && !object.fields().isEmpty()
                                && object.fields().forAll(field -> !container(field.value()))
                        ? Option.of(object.fields().map(JsonTree.Field::key))
                        : Option.none());
        return keys.head().filter(first -> keys.forAll(other -> other.contains(first)));
    }

    /**
     * Whether {@code tree} is packed: an array of more than {@link #STARTS_OPEN} scalars, drawn as many on a row as
     * fit.
     */
    public static boolean packed(JsonTree tree) {
        return tree instanceof JsonTree.JsonArray array
                && array.elements().size() > STARTS_OPEN
                && array.elements().forAll(element -> !container(element));
    }

    /**
     * Whether {@code tree} is a node that folds: an object or an array drawn on a row of its own.
     */
    public static boolean folds(JsonTree tree) {
        return container(tree) && !oneLine(tree);
    }

    /**
     * Whether the node {@code tree} at {@code pointer} is open: as the user {@code chose}, else open when it has no
     * more than {@link #STARTS_OPEN} entries.
     */
    public static boolean open(Map<String, Boolean> chose, String pointer, JsonTree tree) {
        return chose.get(pointer).getOrElse(() -> size(tree) <= STARTS_OPEN);
    }

    /**
     * The field of the object {@code tree} that names it in an array, if it has one: the first it has of
     * {@link #NAMING} with a scalar value, else its first scalar.
     */
    public static Option<JsonTree.Field> naming(JsonTree.JsonObject tree) {
        List<JsonTree.Field> scalars = tree.fields().filter(field -> !container(field.value()));
        return NAMING.flatMap(key -> scalars.find(field -> field.key().equals(key)))
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
     * Every node of {@code root} that folds, by its path, in the order they are drawn; not the root, which is drawn
     * open always, nor what a table or a packed array holds.
     */
    public static Map<String, JsonTree> nodes(JsonTree root) {
        return children(root, "")
                .foldLeft(LinkedHashMap.empty(), (done, child) -> done.merge(nodes(child._2, child._1)));
    }

    private static Map<String, JsonTree> nodes(JsonTree tree, String pointer) {
        if (!folds(tree)) {
            return LinkedHashMap.empty();
        }
        Map<String, JsonTree> own = LinkedHashMap.of(pointer, tree);
        return table(tree).isDefined() || packed(tree)
                ? own
                : children(tree, pointer).foldLeft(own, (done, child) -> done.merge(nodes(child._2, child._1)));
    }

    private static List<Tuple2<String, JsonTree>> children(JsonTree tree, String pointer) {
        return switch (tree) {
            case JsonTree.JsonObject object ->
                object.fields().map(field -> Tuple.of(child(pointer, field.key()), field.value()));
            case JsonTree.JsonArray array ->
                array.elements().zipWithIndex((element, index) -> Tuple.of(child(pointer, "" + index), element));
            case JsonTree.Scalar scalar -> List.empty();
        };
    }
}
