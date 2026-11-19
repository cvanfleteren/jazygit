package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * An editor the user set in the settings file: one that opens a worktree (ADR 110), a file at a line (ADR 145), or
 * both, each by a command of its own.
 *
 * @param name        what the menus and the messages call it
 * @param dirCommand  the program and its arguments that open a worktree, run without a shell; {@link #DIR} in them
 *                    is the worktree. None when it opens no worktree.
 * @param fileCommand the program and its arguments that open a file, run without a shell; {@link #FILE} in them is
 *                    the file, {@link #LINE} the line, and {@link #DIR} the worktree. None when it opens no file.
 * @param terminal    whether it is handed the terminal, as a terminal editor is, rather than started and left running
 * @param shortcut    the letter that chooses it in the menus of the editors, if the user gave it one
 */
public record EditorProgram(
        String name,
        Option<List<String>> dirCommand,
        Option<List<String>> fileCommand,
        boolean terminal,
        Option<Character> shortcut)
        implements Program {

    /**
     * What stands for the worktree in a command.
     */
    public static final String DIR = "{dir}";

    /**
     * What stands for the file in the command that opens a file.
     */
    public static final String FILE = "{file}";

    /**
     * What stands for the line, counted from 1, in the command that opens a file.
     */
    public static final String LINE = "{line}";

    /**
     * An editor that opens a worktree only, by {@code dirCommand}.
     */
    public static EditorProgram ofDir(
            String name, List<String> dirCommand, boolean terminal, Option<Character> shortcut) {
        return new EditorProgram(name, Option.of(dirCommand), Option.none(), terminal, shortcut);
    }

    /**
     * An editor that opens a file only, by {@code fileCommand}.
     */
    public static EditorProgram ofFile(
            String name, List<String> fileCommand, boolean terminal, Option<Character> shortcut) {
        return new EditorProgram(name, Option.none(), Option.of(fileCommand), terminal, shortcut);
    }

    /**
     * Whether it opens a worktree: it has a command for that.
     */
    public boolean opensDirectories() {
        return dirCommand.isDefined();
    }

    /**
     * Whether it opens a file: it has a command for that.
     */
    public boolean opensFiles() {
        return fileCommand.isDefined();
    }

    /**
     * The command that opens the worktree at {@code location}: {@link #DIR} in it replaced by that, wherever and
     * however often it is there, or that added as the last argument without one. None when it opens no worktree.
     */
    public Option<List<String>> commandLine(String location) {
        return dirCommand.map(command -> command.exists(argument -> argument.contains(DIR))
                ? command.map(argument -> argument.replace(DIR, location))
                : command.append(location));
    }

    /**
     * The command that opens {@code file} at {@code line}, in the worktree at {@code worktree}: {@link #FILE},
     * {@link #LINE} and {@link #DIR} in it replaced by those, wherever and however often they are there, or the file
     * added as the last argument without {@link #FILE}. None when it opens no file.
     */
    public Option<List<String>> fileCommandLine(String worktree, String file, int line) {
        return fileCommand.map(command -> {
            List<String> filled = command.map(argument -> fill(argument, worktree, file, line));
            return command.exists(argument -> argument.contains(FILE)) ? filled : filled.append(file);
        });
    }

    /**
     * {@code argument} with each placeholder in it replaced in one pass, so a path that has one in it is left as it
     * is.
     */
    private static String fill(String argument, String worktree, String file, int line) {
        return Programs.fill(argument, LinkedHashMap.of(DIR, worktree, FILE, file, LINE, String.valueOf(line)));
    }
}
