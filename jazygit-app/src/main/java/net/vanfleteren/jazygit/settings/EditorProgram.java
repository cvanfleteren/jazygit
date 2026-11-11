package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * An editor the user set in the settings file, which a worktree is opened in (ADR 110).
 *
 * @param name     what the menu and the messages call it
 * @param command  the program and its arguments, run without a shell; {@link #DIR} in them is the worktree
 * @param terminal whether it is handed the terminal, as a terminal editor is, rather than started and left running
 * @param shortcut the letter that chooses it in the menu of the editors, if the user gave it one
 */
public record EditorProgram(String name, List<String> command, boolean terminal, Option<Character> shortcut) {

    /**
     * What stands for the worktree in a command.
     */
    public static final String DIR = "{dir}";

    /**
     * The command that opens the worktree at {@code location}: {@link #DIR} in it replaced by that, wherever and
     * however often it is there, or that added as the last argument without one.
     */
    public List<String> commandLine(String location) {
        return command.exists(argument -> argument.contains(DIR))
                ? command.map(argument -> argument.replace(DIR, location))
                : command.append(location);
    }

    /**
     * The program it runs, as the user wrote it.
     */
    public String program() {
        return command.head();
    }
}
