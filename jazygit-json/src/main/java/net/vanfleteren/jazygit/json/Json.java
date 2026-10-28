package net.vanfleteren.jazygit.json;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.math.BigDecimal;

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
