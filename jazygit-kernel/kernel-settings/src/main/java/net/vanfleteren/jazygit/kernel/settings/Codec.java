package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * How a setting is read from the settings file and kept there, at the key it has. The value read is as the file
 * holds it: text, a number, a boolean, a {@link java.util.List} for an array and a {@link java.util.Map} for a table.
 *
 * @param <T> what the setting is
 */
public interface Codec<T> {

    /**
     * What {@code value}, found at {@code key} in the settings file, holds of the setting, with the keys of what is
     * wrong in it.
     */
    Decoded<T> decode(String key, Object value);

    /**
     * How {@code value} is kept in the settings file at {@code key}: the value of each key, by its path, each name of
     * a table it is in and then its own; none to keep nothing.
     */
    Map<List<String>, Object> encode(String key, T value);

    /**
     * This codec, for a setting that may be unset: unset, it keeps nothing in the file.
     */
    default Codec<Option<T>> optional() {
        return of(
                (key, value) -> {
                    Decoded<T> decoded = decode(key, value);
                    return new Decoded<>(decoded.value().map(Option::some), decoded.invalid());
                },
                (key, value) -> value.map(set -> encode(key, set)).getOrElse(LinkedHashMap.empty()));
    }

    /**
     * A codec of {@code decode} and {@code encode}.
     */
    static <T> Codec<T> of(
            BiFunction<String, Object, Decoded<T>> decode, BiFunction<String, T, Map<List<String>, Object>> encode) {
        return new Codec<>() {
            @Override
            public Decoded<T> decode(String key, Object value) {
                return decode.apply(key, value);
            }

            @Override
            public Map<List<String>, Object> encode(String key, T value) {
                return encode.apply(key, value);
            }
        };
    }

    /**
     * A setting whose value in the file is valid as a whole or not at all, and then wrong at its key: {@code valid}
     * is what a value holds, if it is valid. It is kept at its key as {@code written} gives it.
     */
    static <T> Codec<T> whole(Function<Object, Option<T>> valid, Function<T, Object> written) {
        return of(
                (key, value) -> {
                    Option<T> read = valid.apply(value);
                    return new Decoded<>(read, Option.when(read.isEmpty(), key).toList());
                },
                (key, value) -> at(key, written.apply(value)));
    }

    /**
     * A setting that is a boolean in the file.
     */
    static Codec<Boolean> bool() {
        return whole(value -> Option.of(value).filter(Boolean.class::isInstance).map(Boolean.class::cast), on -> on);
    }

    /**
     * A setting that is text in the file, which {@code valid} reads, if it is valid. It is kept as {@code written}
     * gives it.
     */
    static <T> Codec<T> text(Function<String, Option<T>> valid, Function<T, String> written) {
        return whole(
                value -> Option.of(value)
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .flatMap(valid),
                written::apply);
    }

    /**
     * A setting the app only reads, which {@code decode} reads; only the user writes it, so it keeps nothing.
     */
    static <T> Codec<T> readOnly(Function<Object, Decoded<T>> decode) {
        return of((key, value) -> decode.apply(value), (key, value) -> LinkedHashMap.empty());
    }

    /**
     * {@code value} kept at {@code key}, a dotted key such as {@code ui.theme}.
     */
    static Map<List<String>, Object> at(String key, Object value) {
        return LinkedHashMap.of(List.of(key.split("\\.")), value);
    }
}
