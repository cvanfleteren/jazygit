package net.vanfleteren.jazygit.core.process;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Puts a program that is left running in a session of its own, through {@code setsid} (ADR 196): in jazygit's it is in
 * the terminal's foreground process group, so the hangup the terminal sends when jazygit, the program that owns it,
 * exits, or when it is closed, would stop it too.
 */
final class OwnSession {

    private static final String SETSID = "setsid";

    private OwnSession() {}

    /**
     * {@code commandLine}, to run in {@code directory}, behind {@code setsid}, when both are found on {@code path} (a
     * {@code PATH}, its directories split by {@code separator}); else as it is, as on Windows and macOS, which have no
     * {@code setsid}. A program that is not found is left as it is, so that it fails to start, as it would without
     * {@code setsid}, rather than {@code setsid} failing to run it.
     */
    static List<String> of(Path directory, List<String> commandLine, String path, String separator) {
        List<Path> directories =
                List.of(path.split(separator)).filter(entry -> !entry.isEmpty()).flatMap(OwnSession::path);
        return found(directories, Path.of("."), SETSID).isDefined()
                        && commandLine
                                .headOption()
                                .flatMap(program -> found(directories, directory, program))
                                .isDefined()
                ? commandLine.prepend(SETSID)
                : commandLine;
    }

    /**
     * {@code commandLine} as {@link #of} gives it, for the {@code PATH} jazygit runs with; as it is on Windows, where
     * a {@code setsid} on the path is Cygwin's or MSYS's, which knows nothing of a Windows console.
     */
    static List<String> of(Path directory, List<String> commandLine) {
        return System.getProperty("os.name", "").startsWith("Windows")
                ? commandLine
                : of(directory, commandLine, Option.of(System.getenv("PATH")).getOrElse(""), File.pathSeparator);
    }

    /**
     * Where {@code program} is, as the system looks for it: relative to {@code directory} when it names a directory,
     * else in the first of {@code directories} that has it.
     */
    private static Option<Path> found(List<Path> directories, Path directory, String program) {
        return program.contains("/")
                ? path(program).map(directory::resolve).filter(OwnSession::runnable)
                : directories.flatMap(dir -> path(program).map(dir::resolve)).find(OwnSession::runnable);
    }

    private static boolean runnable(Path file) {
        return Files.isRegularFile(file) && Files.isExecutable(file);
    }

    private static Option<Path> path(String name) {
        try {
            return Option.of(Path.of(name));
        } catch (InvalidPathException e) {
            return Option.none();
        }
    }
}
