package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
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

    public SettingsFile(Path file) {
        this.file = file;
    }

    @Override
    public Loaded load() {
        if (!Files.exists(file)) {
            return Loaded.DEFAULT;
        }
        return TomlFile.read(file)
                .map(config -> Settings.read(key -> Option.ofOptional(config.getOptional(key))))
                .fold(cause -> new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Unreadable(file, TomlFile.reason(cause)))),
                        read -> new Loaded(read.settings(), Option.when(read.invalid().nonEmpty(),
                                () -> new SettingsProblem.Invalid(file, read.invalid()))));
    }

    @Override
    public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
        // A file that cannot be read is left alone, rather than replaced by one with only these settings.
        Try<CommentedConfig> current = Files.exists(file) ? TomlFile.read(file) : Try.success(TomlFile.newConfig());
        return current
                .map(config -> next.entries().filter(entry -> !previous.entries().contains(entry))
                        .foldLeft(config, (changed, entry) -> {
                            changed.set(entry._1, entry._2);
                            return changed;
                        }))
                .flatMap(config -> TomlFile.write(file, config))
                .toEither()
                .<SettingsProblem>mapLeft(cause -> new SettingsProblem.Unwritable(file, TomlFile.reason(cause)))
                .map(written -> next);
    }
}
