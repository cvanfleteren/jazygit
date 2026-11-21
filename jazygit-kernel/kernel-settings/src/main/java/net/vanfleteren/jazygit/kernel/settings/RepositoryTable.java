package net.vanfleteren.jazygit.kernel.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.function.UnaryOperator;

/**
 * What a feature keeps of a repository in a TOML file of its own, in the repository's directory (ADR 63, 171), as
 * {@link Codec} reads it from the file's top table and keeps it there; the feature names no TOML. The values of the
 * table are as the file holds them: text, a number, a boolean, an {@link Instant} for a date and time, a {@link List}
 * for an array and a {@link Map} for a table, by its keys in their order.
 *
 * <p>Every change reads the file again first, under a lock, so what another jazygit kept meanwhile stays.
 *
 * @param repositories the directory of the repositories' directories
 * @param file         the name of the file in a repository's directory
 * @param codec        how what is kept is read from the file and kept there
 * @param <T>          what is kept
 */
public record RepositoryTable<T>(Path repositories, String file, Codec<T> codec) {

    /**
     * What is kept for {@code repository}, as {@code owner/name}, if its file holds anything the codec reads.
     */
    public Option<T> load(String repository) {
        return path(repository)
                .toOption()
                .filter(Files::exists)
                .flatMap(path -> TomlFile.read(path).toOption())
                .flatMap(config -> codec.decode(table(config)));
    }

    /**
     * What is kept for {@code repository} once {@code change} changed it, which is {@code empty} while nothing is.
     */
    public Try<T> change(String repository, UnaryOperator<T> change, T empty) {
        return path(repository)
                .flatMap(path -> TomlFile.change(path, config -> {
                    T changed = change.apply(codec.decode(table(config)).getOrElse(empty));
                    config.clear();
                    codec.encode(changed)
                            .forEach((key, value) -> config.set(List.of(key).toJavaList(), written(config, value)));
                    return changed;
                }));
    }

    private Try<Path> path(String repository) {
        return SettingsPaths.repositoryFile(repositories, repository, file);
    }

    private static Map<String, Object> table(Config config) {
        return LinkedHashMap.ofEntries(
                List.ofAll(config.entrySet()).map(entry -> Tuple.of(entry.getKey(), read(entry.getValue()))));
    }

    private static Object read(Object value) {
        return switch (value) {
            case Config table -> table(table);
            case java.util.List<?> array -> List.ofAll(array).map(RepositoryTable::read);
            case OffsetDateTime at -> at.toInstant();
            case TemporalAccessor at -> Try.<Object>of(() -> Instant.from(at)).getOrElse(at::toString);
            default -> value;
        };
    }

    private static Object written(CommentedConfig parent, Object value) {
        return switch (value) {
            case Map<?, ?> table -> {
                Config sub = parent.createSubConfig();
                table.forEach(
                        (key, each) -> sub.set(List.of(String.valueOf(key)).toJavaList(), written(parent, each)));
                yield sub;
            }
            case List<?> array -> array.map(each -> written(parent, each)).toJavaList();
            case Instant at -> OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
            default -> value;
        };
    }

    /**
     * How what is kept is read from the top table of its file, and kept there.
     *
     * @param <T> what is kept
     */
    public interface Codec<T> {

        /**
         * What {@code table}, the file's top table, holds, if anything.
         */
        Option<T> decode(Map<String, Object> table);

        /**
         * The top table that keeps {@code value}.
         */
        Map<String, Object> encode(T value);
    }
}
