package net.vanfleteren.jazygit.kernel.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The settings in a TOML file, which the user may edit by hand too.
 *
 * <p>Saving keeps what jazygit does not know of: other keys, their order, and the comments before them. TOML
 * cannot be written back exactly as it was read, though: a comment behind a value moves to the line above it,
 * and comments after the last value are lost.
 */
public final class SettingsFile implements SettingsStore {

    private final Path file;
    private final SettingKeys keys;

    /**
     * The settings of {@code keys} in {@code file}; what the file has at other keys is left as it is.
     */
    public SettingsFile(Path file, SettingKeys keys) {
        this.file = file;
        this.keys = keys;
    }

    /**
     * The kernel's own settings in {@code file}.
     */
    public SettingsFile(Path file) {
        this(file, SettingKeys.KERNEL);
    }

    @Override
    public Option<Path> file() {
        return Option.of(file);
    }

    @Override
    public Either<SettingsProblem, Path> made() {
        // The lock makes the directories the file is in.
        return TomlFile.locked(file, () -> Try.of(() -> Files.exists(file) ? file : Files.createFile(file)))
                .toEither()
                .mapLeft(cause -> new SettingsProblem.Unwritable(file, TomlFile.reason(cause)));
    }

    @Override
    public Loaded load() {
        if (!Files.exists(file)) {
            return new Loaded(Settings.DEFAULT, Option.none(), keys);
        }
        return TomlFile.read(file)
                .map(config -> Settings.read(
                        keys, key -> Option.ofOptional(config.getOptional(key)).map(SettingsFile::plain)))
                .fold(
                        cause -> new Loaded(
                                Settings.DEFAULT,
                                Option.of(new SettingsProblem.Unreadable(file, TomlFile.reason(cause))),
                                keys),
                        read -> new Loaded(
                                read.settings(),
                                Option.when(
                                        read.invalid().nonEmpty(),
                                        () -> new SettingsProblem.Invalid(file, read.invalid())),
                                keys));
    }

    @Override
    public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
        return TomlFile.locked(file, () -> saved(previous, next))
                .toEither()
                .<SettingsProblem>mapLeft(cause -> new SettingsProblem.Unwritable(file, TomlFile.reason(cause)))
                .map(written -> next);
    }

    private Try<Path> saved(Settings previous, Settings next) {
        // A file that cannot be read is left alone, rather than replaced by one with only these settings.
        Try<CommentedConfig> current = Files.exists(file) ? TomlFile.read(file) : Try.success(TomlFile.newConfig());
        return current.flatMap(config -> unchangedSince(config, previous, next))
                .map(config -> next.entries(keys)
                        .filter(entry -> !previous.entries(keys).contains(entry))
                        .foldLeft(config, (changed, entry) -> {
                            changed.set(entry._1.toJavaList(), toml(changed, entry._2));
                            return changed;
                        }))
                // A setting back at its default has no entry anymore, nor a table it leaves empty.
                .map(config -> previous.entries(keys)
                        .keySet()
                        .removeAll(next.entries(keys).keySet())
                        .foldLeft(config, (changed, key) -> {
                            changed.remove(key.toJavaList());
                            return withoutEmptyTables(changed, key.dropRight(1));
                        }))
                .flatMap(config -> TomlFile.write(file, config));
    }

    /**
     * {@code config}, the file as it is now, unless a guarded setting that {@code next} changes from {@code previous}
     * changed in it since {@code previous} was read or written: another session, or the user, wrote there meanwhile,
     * and writing the setting as a whole would lose that (ADR 164).
     */
    private Try<CommentedConfig> unchangedSince(CommentedConfig config, Settings previous, Settings next) {
        Option<String> changed = keys.all()
                .filter(key -> key.codec().guarded() && key.changed(previous, next))
                // What each would write, not the values read: an entry that is wrong names its place in the list,
                // which a delete or a move above it changes, while what is written stays the same.
                .filter(key -> changedIn(config, key, previous))
                .map(SettingKey::path)
                .headOption();
        return changed.isEmpty()
                ? Try.success(config)
                : Try.failure(new IllegalStateException(changed.get()
                        + " changed in the settings file since it was read; it is read again as the settings panel"
                        + " opens"));
    }

    /**
     * Whether {@code config} keeps {@code key} otherwise than {@code previous} does.
     */
    private static <T> boolean changedIn(CommentedConfig config, SettingKey<T> key, Settings previous) {
        return !written(key, asRead(config, key)).equals(written(key, previous.get(key)));
    }

    /**
     * What {@code value} of {@code key} is kept as in the settings file.
     */
    private static <T> Map<List<String>, Object> written(SettingKey<T> key, T value) {
        return key.codec().encode(key.path(), value);
    }

    /**
     * What {@code config} has of {@code key}, as a read would take it: its initial value when it has none, or none
     * valid.
     */
    private static <T> T asRead(CommentedConfig config, SettingKey<T> key) {
        return Option.ofOptional(
                        config.getOptional(List.of(key.path().split("\\.")).toJavaList()))
                .flatMap(found -> key.codec().decode(key.path(), plain(found)).value())
                .getOrElse(key.initial());
    }

    /**
     * {@code value} as the TOML library writes it in {@code config}: a {@link java.util.Map} as a table, also one in
     * an array, as an array of tables.
     */
    private static Object toml(CommentedConfig config, Object value) {
        if (value instanceof java.util.Map<?, ?> table) {
            CommentedConfig written = config.createSubConfig();
            table.forEach(
                    (key, field) -> written.set(List.of(String.valueOf(key)).toJavaList(), toml(config, field)));
            return written;
        }
        return value instanceof java.util.List<?> array
                ? List.ofAll(array).map(item -> toml(config, item)).toJavaList()
                : value;
    }

    /**
     * {@code config} without the table at {@code path}, nor the tables it is in, once they are empty.
     */
    private static CommentedConfig withoutEmptyTables(CommentedConfig config, List<String> path) {
        if (path.nonEmpty() && config.get(path.toJavaList()) instanceof UnmodifiableConfig table && table.isEmpty()) {
            config.remove(path.toJavaList());
            return withoutEmptyTables(config, path.dropRight(1));
        }
        return config;
    }

    /**
     * {@code value} as read, with a table as a {@link java.util.Map}, also one in an array, as the editors are
     * (ADR 110), so the settings know nothing of the TOML library.
     */
    static Object plain(Object value) {
        return value instanceof UnmodifiableConfig table
                ? List.ofAll(table.valueMap().entrySet())
                        .toJavaMap(
                                java.util.LinkedHashMap::new,
                                entry -> Tuple.of(entry.getKey(), plain(entry.getValue())))
                : value instanceof java.util.List<?> array
                        ? List.ofAll(array).map(SettingsFile::plain).toJavaList()
                        : value;
    }
}
