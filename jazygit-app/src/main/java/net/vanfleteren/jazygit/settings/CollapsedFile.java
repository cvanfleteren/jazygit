package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import io.vavr.collection.HashMap;
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

/**
 * The review threads and comments collapsed or opened against their default, in a file of each repository (ADR 63): a
 * table per pull request, with when one was last flipped, and a sub-table with whether each id flipped was collapsed by
 * default then:
 *
 * <pre>
 * [42]
 * touched = 2026-10-31T10:00:00Z
 *
 * [42.flipped]
 * PRRT_kwDO = true
 * </pre>
 *
 * Every change reads the file again first, under a lock, so what another jazygit kept meanwhile stays.
 */
public final class CollapsedFile implements CollapsedStore {

    private static final String FILE = "collapsed.toml";
    private static final String TOUCHED = "touched";
    private static final String FLIPPED = "flipped";

    private final Path repositories;

    /**
     * What is flipped of the pull requests of each repository, in its directory in {@code repositories}.
     */
    public CollapsedFile(Path repositories) {
        this.repositories = repositories;
    }

    private Try<Path> file(String repository) {
        return SettingsPaths.repositoryFile(repositories, repository, FILE);
    }

    // As a list of keys, so an id with a dot in it is one key.
    private static List<String> path(String number, String... keys) {
        return List.of(keys).prepend(number);
    }

    private static Map<String, Boolean> flipped(Config config, String number) {
        return config.getOptional(path(number, FLIPPED).toJavaList())
                .filter(Config.class::isInstance)
                .map(found -> List.ofAll(((Config) found).entrySet())
                        .filter(entry -> entry.getValue() instanceof Boolean)
                        .<String, Boolean>toMap(entry -> entry.getKey(), entry -> (Boolean) entry.getValue()))
                .orElse(HashMap.empty());
    }

    private static Option<Instant> touched(Config config, String number) {
        return Option.ofOptional(config.getOptional(path(number, TOUCHED).toJavaList()))
                .flatMap(at -> Try.of(() -> at instanceof TemporalAccessor temporal
                                ? Instant.from(temporal)
                                : Instant.parse((String) at))
                        .toOption());
    }

    @Override
    public Map<String, Boolean> load(String repository, int number) {
        return file(repository)
                .toOption()
                .filter(Files::exists)
                .flatMap(file -> TomlFile.read(file).toOption())
                .map(config -> flipped(config, String.valueOf(number)))
                .getOrElse(HashMap.empty());
    }

    @Override
    public Try<Map<String, Boolean>> save(String repository, int number, Map<String, Boolean> flipped, Instant at) {
        return file(repository)
                .flatMap(file -> TomlFile.change(file, config -> {
                    Instant oldest = at.minus(KEPT);
                    String key = String.valueOf(number);
                    // A pull request without a time it was flipped at is as old as can be.
                    List.ofAll(config.valueMap().keySet())
                            .filter(kept -> kept.equals(key)
                                    || touched(config, kept).forAll(touched -> touched.isBefore(oldest)))
                            .forEach(kept -> config.remove(path(kept).toJavaList()));
                    if (!flipped.isEmpty()) {
                        config.set(path(key, TOUCHED).toJavaList(), OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
                        config.set(path(key, FLIPPED).toJavaList(), table(config, flipped));
                    }
                    return flipped;
                }));
    }

    private static Config table(CommentedConfig parent, Map<String, Boolean> flipped) {
        Config table = parent.createSubConfig();
        flipped.forEach((id, collapsed) -> table.set(List.of(id).toJavaList(), collapsed));
        return table;
    }
}
