package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * An agent the user set in the settings file, which a failed build is handed to (ADR 161).
 *
 * @param name     what the menus and the messages call it
 * @param command  the program and its arguments, run without a shell; {@link #PROMPT} in them is the prompt, and
 *                 {@link #DIR} the worktree it runs in
 * @param terminal whether it is handed the terminal, rather than started and left running
 * @param shortcut the letter that chooses it in the menu of the agents, if the user gave it one
 * @param prompt   what it is asked, with the placeholders of the failed build in it; none for jazygit's own text
 */
public record AgentProgram(
        String name, List<String> command, boolean terminal, Option<Character> shortcut, Option<String> prompt)
        implements Program {

    /**
     * What stands for the prompt in the command.
     */
    public static final String PROMPT = "{prompt}";

    /**
     * What stands for the worktree in the command.
     */
    public static final String DIR = "{dir}";

    /**
     * The command that asks {@code prompt} in the worktree at {@code worktree}: {@link #PROMPT} and {@link #DIR} in it
     * replaced by those, wherever and however often they are there, or the prompt added as the last argument without
     * {@link #PROMPT}. A {@code {…}} that is neither stays as it is.
     */
    public List<String> commandLine(String worktree, String prompt) {
        List<String> filled =
                command.map(argument -> Programs.fill(argument, LinkedHashMap.of(PROMPT, prompt, DIR, worktree)));
        return command.exists(argument -> argument.contains(PROMPT)) ? filled : filled.append(prompt);
    }
}
