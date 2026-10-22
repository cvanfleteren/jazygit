package net.vanfleteren.jazygit;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

/**
 * The debug log that {@code --debug} asks for. SLF4J's simple logger writes it to a file, as the terminal is the
 * TUI's; without it, nothing is logged at all, so nothing can reach the terminal either.
 *
 * <p>It has to be set up before anything logs: the simple logger reads its settings when the first logger is made.
 */
final class DebugLog {

    private static final String SIMPLE_LOGGER = "org.slf4j.simpleLogger.";

    private DebugLog() {}

    /**
     * Logs to {@code file} from now on, which it empties first, or nothing at all when there is none.
     *
     * @return why {@code file} can't be written; the simple logger would write to the terminal instead
     */
    static Try<Void> start(Option<Path> file) {
        return file.map(DebugLog::empty)
                .getOrElse(Try.success(null))
                .andThen(() -> properties(file).forEach(System::setProperty))
                .andThen(() -> file.forEach(log -> logStart()));
    }

    /**
     * The system properties that set up the simple logger to write to {@code file}, or to log nothing.
     */
    static Map<String, String> properties(Option<Path> file) {
        return file.fold(
                () -> HashMap.of(SIMPLE_LOGGER + "defaultLogLevel", "off"),
                log -> HashMap.of(
                        SIMPLE_LOGGER + "defaultLogLevel", "debug",
                        SIMPLE_LOGGER + "logFile", log.toString(),
                        SIMPLE_LOGGER + "showDateTime", "true",
                        SIMPLE_LOGGER + "dateTimeFormat", "HH:mm:ss.SSS",
                        // zt-exec logs the environment of every command it starts, which holds the gh token.
                        SIMPLE_LOGGER + "log.org.zeroturnaround.exec", "info"));
    }

    /**
     * Creates {@code file}, or empties it, which also tells whether it can be written.
     */
    private static Try<Void> empty(Path file) {
        Path absolute = file.toAbsolutePath();
        return Try.run(() -> Files.createDirectories(absolute.getParent()))
                .andThenTry(() -> Files.write(absolute, new byte[0]));
    }

    /**
     * What a bug report needs to know first: which jazygit runs where.
     */
    private static void logStart() {
        LoggerFactory.getLogger(DebugLog.class)
                .info(
                        "jazygit {} on Java {}, {} {}",
                        Option.of(DebugLog.class.getPackage().getImplementationVersion())
                                .getOrElse("(development build)"),
                        Runtime.version(),
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"));
    }
}
