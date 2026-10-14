package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.IndentStyle;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;

/**
 * The settings in a TOML file, which the user may edit by hand too.
 *
 * <p>Saving keeps what jazygit does not know of: other keys, their order, and the comments before them. TOML
 * cannot be written back exactly as it was read, though: a comment behind a value moves to the line above it,
 * and comments after the last value are lost.
 */
public final class SettingsFile implements SettingsStore {

    private final Path file;

    public SettingsFile(Path file) {
        this.file = file;
    }

    @Override
    public Loaded load() {
        if (!Files.exists(file)) {
            return Loaded.DEFAULT;
        }
        return read()
                .map(config -> Settings.read(key -> Option.ofOptional(config.getOptional(key))))
                .fold(cause -> new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Unreadable(file, reason(cause)))),
                        read -> new Loaded(read.settings(), Option.when(read.invalid().nonEmpty(),
                                () -> new SettingsProblem.Invalid(file, read.invalid()))));
    }

    @Override
    public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
        // A file that cannot be read is left alone, rather than replaced by one with only these settings.
        Try<CommentedConfig> current = Files.exists(file) ? read() : Try.success(newConfig());
        return current
                .map(config -> next.entries().filter(entry -> !previous.entries().contains(entry))
                        .foldLeft(config, (changed, entry) -> {
                            changed.set(entry._1, entry._2);
                            return changed;
                        }))
                .flatMap(this::write)
                .toEither()
                .<SettingsProblem>mapLeft(cause -> new SettingsProblem.Unwritable(file, reason(cause)))
                .map(written -> next);
    }

    private Try<CommentedConfig> read() {
        return Try.withResources(() -> Files.newBufferedReader(file, StandardCharsets.UTF_8))
                .of((Reader reader) -> {
                    CommentedConfig config = newConfig();
                    new TomlParser().parse(reader, config, ParsingMode.REPLACE);
                    return config;
                });
    }

    /**
     * Writes {@code config} next to the file and then moves it over it, so the file is never written halfway.
     */
    private Try<Path> write(CommentedConfig config) {
        return Try.of(() -> {
            StringWriter text = new StringWriter();
            TomlWriter writer = new TomlWriter();
            writer.setIndent(IndentStyle.NONE);
            writer.write(config, text);
            Path dir = Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.writeString(Files.createTempFile(dir, file.getFileName().toString(), ".tmp"),
                    text.toString(), StandardCharsets.UTF_8);
            return Try.of(() -> move(temp)).onFailure(failed -> Try.run(() -> Files.deleteIfExists(temp))).get();
        });
    }

    private Path move(Path temp) throws java.io.IOException {
        try {
            return Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            return Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * An empty config that keeps its keys in the order they were read or added.
     */
    private static CommentedConfig newConfig() {
        return CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
    }

    private static String reason(Throwable cause) {
        return Option.of(cause.getMessage()).filter(message -> !message.isBlank())
                .getOrElse(() -> cause.getClass().getSimpleName());
    }
}
