package net.vanfleteren.jazygit.settings;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.IndentStyle;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;

/**
 * Reading and writing a TOML file, which is never left written halfway.
 */
final class TomlFile {

    private TomlFile() {}

    static Try<CommentedConfig> read(Path file) {
        return Try.withResources(() -> Files.newBufferedReader(file, StandardCharsets.UTF_8))
                .of((Reader reader) -> {
                    CommentedConfig config = newConfig();
                    new TomlParser().parse(reader, config, ParsingMode.REPLACE);
                    return config;
                });
    }

    /**
     * Writes {@code config} next to {@code file} and then moves it over it, so the file is never written halfway.
     */
    static Try<Path> write(Path file, CommentedConfig config) {
        return Try.of(() -> {
            StringWriter text = new StringWriter();
            TomlWriter writer = new TomlWriter();
            writer.setIndent(IndentStyle.NONE);
            writer.write(config, text);
            Path dir = Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.writeString(
                    Files.createTempFile(dir, file.getFileName().toString(), ".tmp"),
                    text.toString(),
                    StandardCharsets.UTF_8);
            return Try.of(() -> move(temp, file))
                    .onFailure(failed -> {
                        Try<Void> _ = Try.run(() -> Files.deleteIfExists(temp));
                    })
                    .get();
        });
    }

    private static Path move(Path temp, Path file) throws IOException {
        try {
            return Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            return Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * An empty config that keeps its keys in the order they were read or added.
     */
    static CommentedConfig newConfig() {
        return CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
    }

    static String reason(Throwable cause) {
        return Option.of(cause.getMessage())
                .filter(message -> !message.isBlank())
                .getOrElse(() -> cause.getClass().getSimpleName());
    }
}
