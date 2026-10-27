package net.vanfleteren.jazygit;

import io.vavr.control.Try;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.slf4j.LoggerFactory;

/**
 * Where the commands that threw are written down, as the terminal is the TUI's.
 */
final class BugLog {

    private static final Path FILE = Path.of(System.getProperty("java.io.tmpdir"), "jazygit-bugs.log");

    private BugLog() {}

    /**
     * Writes how a command threw to the bug log; failing that, it is lost. The debug log gets it too, after what led
     * up to it.
     */
    static void log(Throwable bug) {
        LoggerFactory.getLogger(BugLog.class).error("A command threw", bug);
        StringWriter trace = new StringWriter();
        bug.printStackTrace(new PrintWriter(trace));
        Try.run(() -> Files.writeString(
                FILE,
                Instant.now() + " " + trace + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND));
    }
}
