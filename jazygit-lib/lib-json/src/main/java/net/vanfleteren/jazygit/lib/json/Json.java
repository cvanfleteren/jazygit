package net.vanfleteren.jazygit.lib.json;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * A JSON value, as plain values that never change (ADR 42). The fields of an object keep the order they were read in.
 */
public sealed interface Json {

    /**
     * {@code text} as the one JSON value it holds, or why it does not hold one.
     */
    static Either<DecodeError, Json> parse(String text) {
        return JsonReader.read(text);
    }

    /**
     * What kind of value this is, as an error tells it: {@code an object}, {@code a string}, {@code null}, ...
     */
    default String kind() {
        return switch (this) {
            case JObject o -> "an object";
            case JArray a -> "an array";
            case JString s -> "a string";
            case JNumber n -> "a number";
            case JBool b -> "a boolean";
            case JNull n -> "null";
        };
    }

    /**
     * This value as JSON text, on one line, its fields in their order: what {@link #parse} reads back as it.
     */
    default String text() {
        return switch (this) {
            case JObject(var fields) ->
                fields.map(field -> quoted(field._1()) + ":" + field._2().text())
                        .mkString("{", ",", "}");
            case JArray(var elements) -> elements.map(Json::text).mkString("[", ",", "]");
            case JString(var value) -> quoted(value);
            case JNumber(var value) -> value.toString();
            case JBool(var value) -> String.valueOf(value);
            case JNull n -> "null";
        };
    }

    /**
     * {@code value} as a JSON string: quotes and backslashes escaped, and the control characters, as JSON needs.
     */
    private static String quoted(String value) {
        return value.chars()
                .mapToObj(c -> switch (c) {
                    case '"' -> "\\\"";
                    case '\\' -> "\\\\";
                    case '\n' -> "\\n";
                    case '\r' -> "\\r";
                    case '\t' -> "\\t";
                    default -> c < 0x20 ? "\\u%04x".formatted(c) : String.valueOf((char) c);
                })
                .collect(Collectors.joining("", "\"", "\""));
    }

    record JObject(LinkedHashMap<String, Json> fields) implements Json {}

    record JArray(List<Json> elements) implements Json {}

    record JString(String value) implements Json {}

    /**
     * A number as it was written. Two are equal as {@link BigDecimal}s are, scale and all: {@code 1.0} is not
     * {@code 1.00}. Decoders read it with {@code intValueExact} and {@code longValueExact}, which do not care.
     */
    record JNumber(BigDecimal value) implements Json {}

    record JBool(boolean value) implements Json {}

    record JNull() implements Json {}
}
