package net.vanfleteren.jazygit.git;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders a command as the line one would type in a shell.
 */
public final class CommandLine {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_@%+=:,./-]+");

    private CommandLine() {
    }

    public static String format(List<String> command) {
        return command.stream().map(CommandLine::quote).reduce((a, b) -> a + " " + b).orElse("");
    }

    private static String quote(String arg) {
        return SAFE.matcher(arg).matches() ? arg : "'" + arg.replace("'", "'\\''") + "'";
    }
}
