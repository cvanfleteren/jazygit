package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.time.Duration;

/**
 * A git invocation, as data; {@link GitProcess} runs it.
 *
 * @param args        the arguments after {@code git}
 * @param environment set on top of the inherited environment
 */
record GitCommand(List<String> args, Map<String, String> environment, Duration timeout) {

    // A command that changes the repository can run hooks, like a pre-commit hook running the tests.
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    // A read keeps the others waiting, and only reads.
    private static final Duration READING_TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, String> DEFAULT_ENVIRONMENT = HashMap.of(
            // Fail instead of waiting for credentials that cannot be typed.
            "GIT_TERMINAL_PROMPT", "0",
            // Fail instead of opening an editor on the terminal the TUI owns; a command that is to edit
            // something says how.
            "GIT_EDITOR", "false",
            // Git's messages are matched in English.
            "LC_ALL", "C");

    static GitCommand git(String... args) {
        return git(List.of(args));
    }

    static GitCommand git(List<String> args) {
        return new GitCommand(args, DEFAULT_ENVIRONMENT, DEFAULT_TIMEOUT);
    }

    /**
     * A command that only reads. It takes no optional locks, so it does not get in the way of git commands
     * the user runs meanwhile, as the periodic reads would otherwise do; and it gets less time than one that
     * changes the repository.
     */
    static GitCommand reading(String... args) {
        return reading(List.of(args));
    }

    static GitCommand reading(List<String> args) {
        return git(args.prepend("--no-optional-locks")).withTimeout(READING_TIMEOUT);
    }

    GitCommand withEnvironment(Map<String, String> variables) {
        return new GitCommand(args, environment.merge(variables, (current, given) -> given), timeout);
    }

    /**
     * This command with its paths taken as the files of that name, never as patterns: discarding a file named
     * {@code *.txt} must not discard every text file. Not for every command, as some, like
     * {@code git stash --include-untracked}, use patterns themselves.
     */
    GitCommand withLiteralPaths() {
        return withEnvironment(HashMap.of("GIT_LITERAL_PATHSPECS", "1"));
    }

    /**
     * This command with the configuration {@code key} set to {@code value}, whatever the user's says:
     * {@code git -c <key>=<value> ...}.
     */
    GitCommand withConfig(String key, String value) {
        // After the ones set before, so they are listed in the order they were set.
        int configured = args.grouped(2).takeWhile(pair -> pair.head().equals("-c")).size() * 2;
        return new GitCommand(args.insertAll(configured, List.of("-c", key + "=" + value)), environment, timeout);
    }

    GitCommand withTimeout(Duration timeout) {
        return new GitCommand(args, environment, timeout);
    }

    List<String> commandLine() {
        return args.prepend("git");
    }

    /**
     * The command, as it is logged once it ran.
     */
    ExecutedCommand executed() {
        return new ExecutedCommand(commandLine());
    }

    /**
     * The git subcommand, e.g. {@code checkout}.
     */
    String operation() {
        // The value of a "-c" is no subcommand either.
        return args.zip(args.prepend("").init())
                .find(arg -> !arg._1.startsWith("-") && !arg._2.equals("-c"))
                .map(arg -> arg._1)
                .getOrElse("git");
    }
}
