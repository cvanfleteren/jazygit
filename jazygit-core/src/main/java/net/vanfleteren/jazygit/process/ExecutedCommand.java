package net.vanfleteren.jazygit.process;

import io.vavr.collection.List;

/**
 * A command that was executed through the command line, as it was run.
 *
 * @param commandLine the program and its arguments, e.g. {@code git push origin main}
 */
public record ExecutedCommand(List<String> commandLine) {

    public static ExecutedCommand of(String... commandLine) {
        return new ExecutedCommand(List.of(commandLine));
    }

    /**
     * The command as one would type it in a shell.
     */
    public String line() {
        return CommandLine.format(commandLine);
    }
}
