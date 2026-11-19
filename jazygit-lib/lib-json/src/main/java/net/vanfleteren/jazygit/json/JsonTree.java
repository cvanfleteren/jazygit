package net.vanfleteren.jazygit.json;

import io.vavr.collection.List;
import io.vavr.control.Either;

/**
 * JSON as it is written, read to be drawn (ADR 122): each key and value with the line it starts on, a container with
 * the line it ends on too, and a number as it is written. Not for decoding: that reads {@link Json}.
 */
public sealed interface JsonTree {

    /**
     * {@code text} as the one JSON value it holds, or why it does not hold one.
     */
    static Either<NotJson, JsonTree> read(String text) {
        return JsonReader.tree(text);
    }

    /**
     * The line it starts on, counted from 1.
     */
    int line();

    /**
     * An object: its fields in the order they are written. Of two fields with the same key, the last holds, where it
     * is written, as it does when {@link Json} is read; it keeps the lines of the keys before it.
     *
     * @param end the line of its closing brace
     */
    record JsonObject(int line, int end, List<Field> fields) implements JsonTree {}

    /**
     * @param end the line of its closing bracket
     */
    record JsonArray(int line, int end, List<JsonTree> elements) implements JsonTree {}

    /**
     * A string, a number, {@code true}, {@code false} or {@code null}.
     *
     * @param text a string's value, its escapes undone; a number as it is written, {@code 1e3} staying {@code 1e3};
     *             else {@code true}, {@code false} or {@code null}
     */
    record Scalar(int line, Kind kind, String text) implements JsonTree {}

    /**
     * The kinds of scalar.
     */
    enum Kind {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    /**
     * A field of an object.
     *
     * @param line    the line of its key
     * @param ignored the lines of the keys of earlier fields with the same key, which this one holds over
     */
    record Field(String key, int line, JsonTree value, List<Integer> ignored) {}
}
