package net.vanfleteren.jazygit.gh;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.time.Duration;

/**
 * A {@code gh} invocation, as data; {@link GhProcess} runs it.
 *
 * @param args        the arguments after {@code gh}
 * @param environment set on top of the inherited environment
 */
record GhCommand(List<String> args, Map<String, String> environment, Duration timeout) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Map<String, String> DEFAULT_ENVIRONMENT = HashMap.of(
            // Fail instead of asking anything on a terminal that cannot be typed in.
            "GH_PROMPT_DISABLED", "1",
            "NO_COLOR", "1",
            "GH_NO_UPDATE_NOTIFIER", "1");

    static GhCommand gh(String... args) {
        return new GhCommand(List.of(args), DEFAULT_ENVIRONMENT, DEFAULT_TIMEOUT);
    }

    /**
     * The command line that runs this command with the gh at {@code executable}.
     */
    List<String> commandLine(String executable) {
        return args.prepend(executable);
    }

    /**
     * The command, as it is logged once it ran: as {@code gh}, wherever the executable is.
     */
    ExecutedCommand executed() {
        return new ExecutedCommand(commandLine("gh"));
    }

    /**
     * The gh command, e.g. {@code pr create}.
     */
    String operation() {
        return args.takeWhile(arg -> !arg.startsWith("-")).take(2).mkString(" ");
    }
}
