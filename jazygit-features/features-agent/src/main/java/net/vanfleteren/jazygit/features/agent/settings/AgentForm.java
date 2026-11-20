package net.vanfleteren.jazygit.features.agent.settings;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.process.CommandLine;
import net.vanfleteren.jazygit.features.agent.investigate.Prompt;
import net.vanfleteren.jazygit.kernel.settings.Programs;
import net.vanfleteren.jazygit.kernel.state.settings.Field;
import net.vanfleteren.jazygit.kernel.state.settings.FieldProblem;
import net.vanfleteren.jazygit.kernel.state.settings.Form;
import net.vanfleteren.jazygit.kernel.state.settings.ListSetting;

/**
 * The agents as the settings panel edits them (ADR 164): the list, and the form of one agent, with what is wrong in
 * it. A name, a command and a shortcut that is not taken are needed to save an agent; a placeholder in its prompt
 * that jazygit does not fill only warns, as such braces are kept (ADR 161).
 */
public final class AgentForm {

    static final String NAME = "agent.settings.field.name";
    static final String COMMAND = "agent.settings.field.command";
    static final String TERMINAL = "agent.settings.field.terminal";
    static final String SHORTCUT = "agent.settings.field.shortcut";
    static final String PROMPT = "agent.settings.field.prompt";

    /**
     * What a new agent starts as: nothing set, started and left running.
     */
    static final AgentProgram BLANK = new AgentProgram("", List.empty(), false, Option.none(), Option.none());

    /**
     * The form of one agent. The command is edited as the line a shell would split into its words, so a word with
     * a space in it is quoted; the prompt as one line, empty for jazygit's own.
     */
    public static final Form<AgentProgram> FORM = new Form<>(
            BLANK,
            List.of(
                    new Field.Text<>(NAME, AgentProgram::name, (agent, name) -> agent.withName(name.strip())),
                    new Field.Text<>(
                            COMMAND,
                            agent -> CommandLine.format(agent.command()),
                            (agent, line) -> agent.withCommand(CommandLine.words(line))),
                    new Field.Toggle<>(TERMINAL, AgentProgram::terminal, AgentProgram::withTerminal),
                    new Field.Letter<>(SHORTCUT, AgentProgram::shortcut, AgentProgram::withShortcut),
                    new Field.Text<>(
                            PROMPT,
                            agent -> agent.prompt().getOrElse(""),
                            (agent, text) -> agent.withPrompt(Option.of(text).filter(set -> !set.isBlank())))),
            AgentForm::problems);

    /**
     * The agents, in a row of the settings panel and the dialog it opens.
     */
    public static final ListSetting<AgentProgram> LIST =
            new ListSetting<>(Agents.SETTING, "agent.settings.title", AgentProgram::name, FORM);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_-]*}");

    private AgentForm() {}

    /**
     * What is wrong with {@code agent}, given {@code others}, the other agents of the list.
     */
    static List<FieldProblem> problems(AgentProgram agent, List<AgentProgram> others) {
        return List.<FieldProblem>empty()
                .appendAll(Option.when(
                        agent.name().isBlank(), () -> FieldProblem.error(NAME, "agent.settings.problem.empty")))
                .appendAll(Option.when(
                        !agent.name().isBlank()
                                && others.exists(other -> other.name().equals(agent.name())),
                        () -> FieldProblem.error(NAME, "agent.settings.problem.nameTaken")))
                .appendAll(Option.when(
                        agent.command().headOption().forAll(String::isBlank),
                        () -> FieldProblem.error(COMMAND, "agent.settings.problem.empty")))
                .appendAll(agent.shortcut().toList().flatMap(letter -> shortcut(letter, others)))
                .appendAll(agent.prompt().toList().flatMap(AgentForm::unknown));
    }

    private static List<FieldProblem> shortcut(char letter, List<AgentProgram> others) {
        if (Programs.shortcut(String.valueOf(letter), HashSet.empty()).isEmpty()) {
            return List.of(FieldProblem.error(SHORTCUT, "agent.settings.problem.shortcut", String.valueOf(letter)));
        }
        return others.find(other -> other.shortcut().contains(letter))
                .map(other -> FieldProblem.error(SHORTCUT, "agent.settings.problem.shortcutTaken", other.name()))
                .toList();
    }

    /**
     * A warning for the placeholders in {@code prompt} that jazygit does not fill, if any.
     */
    private static List<FieldProblem> unknown(String prompt) {
        Matcher found = PLACEHOLDER.matcher(prompt);
        List<String> unknown = List.ofAll(found.results().map(match -> match.group()))
                .filter(placeholder -> !Prompt.PLACEHOLDERS.contains(placeholder))
                .distinct();
        return Option.when(
                        unknown.nonEmpty(),
                        () -> FieldProblem.warning(
                                PROMPT,
                                "agent.settings.problem.placeholder",
                                unknown.mkString(", "),
                                Prompt.PLACEHOLDERS.mkString(", ")))
                .toList();
    }
}
