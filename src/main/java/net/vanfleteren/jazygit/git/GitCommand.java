package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;

import java.time.Duration;

/**
 * A git invocation, as data; {@link GitProcess} runs it.
 *
 * @param args        the arguments after {@code git}
 * @param environment set on top of the inherited environment
 */
record GitCommand(List<String> args, Map<String, String> environment, Duration timeout) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, String> DEFAULT_ENVIRONMENT = HashMap.of(
            // Fail instead of waiting for credentials that cannot be typed.
            "GIT_TERMINAL_PROMPT", "0",
            // Git's messages are matched in English.
            "LC_ALL", "C");

    static GitCommand git(String... args) {
        return git(List.of(args));
    }

    static GitCommand git(List<String> args) {
        return new GitCommand(args, DEFAULT_ENVIRONMENT, DEFAULT_TIMEOUT);
    }

    GitCommand withEnvironment(Map<String, String> variables) {
        return new GitCommand(args, environment.merge(variables, (current, given) -> given), timeout);
    }

    GitCommand withTimeout(Duration timeout) {
        return new GitCommand(args, environment, timeout);
    }

    List<String> commandLine() {
        return args.prepend("git");
    }

    /**
     * The command as one would type it in a shell.
     */
    String line() {
        return CommandLine.format(commandLine());
    }

    /**
     * The git subcommand, e.g. {@code checkout}.
     */
    String operation() {
        return args.find(arg -> !arg.startsWith("-")).getOrElse("git");
    }
}
