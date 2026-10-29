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
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reading and writing a TOML file, which is never left written halfway.
 */
final class TomlFile {

    private static final int MAX_LINKS = 40;

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
     * A symlink is followed, so the file it leads to is the one replaced, and that file keeps its permissions.
     */
    static Try<Path> write(Path file, CommentedConfig config) {
        return Try.of(() -> {
            StringWriter text = new StringWriter();
            TomlWriter writer = new TomlWriter();
            writer.setIndent(IndentStyle.NONE);
            writer.write(config, text);
            Path target = target(file);
            Path dir = Files.createDirectories(target.getParent());
            // Created like any new file, with the default permissions rather than the owner-only of a temp file.
            Path temp = Files.createFile(dir.resolve(target.getFileName() + "." + UUID.randomUUID() + ".tmp"));
            return Try.of(() -> {
                        keepPermissions(target, temp);
                        Files.writeString(temp, text.toString(), StandardCharsets.UTF_8);
                        return move(temp, target);
                    })
                    .onFailure(failed -> Try.run(() -> Files.deleteIfExists(temp)))
                    .get();
        });
    }

    /**
     * Runs {@code action} holding a lock on a {@code .lock} file next to {@code file} itself, not next to what a
     * symlink leads to, so no file is left in a dotfiles repository. Two changes, in this app or in two, then do not
     * both read the file and each write over the other.
     */
    static synchronized <T> Try<T> locked(Path file, Supplier<Try<T>> action) {
        return Try.withResources(() -> lock(file)).of(lock -> action.get()).flatMap(result -> result);
    }

    /**
     * Writes {@code file} with what {@code change} does to it, keeping the rest, and gives what {@code change} returned
     * once it is written. A file that cannot be read is written anew, as nothing in it is worth keeping.
     */
    static <T> Try<T> change(Path file, Function<CommentedConfig, T> change) {
        return locked(file, () -> {
            CommentedConfig config = Files.exists(file) ? read(file).getOrElse(TomlFile::newConfig) : newConfig();
            T changed = change.apply(config);
            return write(file, config).map(written -> changed);
        });
    }

    /**
     * A channel holding the lock until it is closed. The JVM itself allows one lock per file, hence {@code synchronized}
     * on {@link #locked}.
     */
    private static FileChannel lock(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        FileChannel channel = FileChannel.open(
                absolute.resolveSibling(absolute.getFileName() + ".lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
        try {
            channel.lock();
            return channel;
        } catch (IOException | RuntimeException failed) {
            channel.close();
            throw failed;
        }
    }

    /**
     * The file a symlink leads to, or {@code file} itself when it is no symlink.
     */
    private static Path target(Path file) throws IOException {
        return target(file.toAbsolutePath(), MAX_LINKS);
    }

    private static Path target(Path absolute, int linksLeft) throws IOException {
        if (Files.exists(absolute)) {
            return absolute.toRealPath();
        }
        if (!Files.isSymbolicLink(absolute)) {
            return absolute;
        }
        if (linksLeft == 0) {
            throw new IOException("Too many symbolic links: " + absolute);
        }
        return target(absolute.resolveSibling(Files.readSymbolicLink(absolute)), linksLeft - 1);
    }

    private static void keepPermissions(Path from, Path to) throws IOException {
        if (Files.exists(from) && Files.getFileStore(from).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        }
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
