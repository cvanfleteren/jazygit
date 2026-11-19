package net.vanfleteren.jazygit.kernel.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The agents of the settings file, which a failed build is handed to (ADR 161).
 */
class AgentsTest {

    private static java.util.Map<String, Object> table(Object... keysAndValues) {
        java.util.Map<String, Object> table = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            table.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return table;
    }

    @Test
    void thePromptAndTheWorktreeTakeTheirPlacesInTheCommand() {
        AgentProgram agent = new AgentProgram(
                "Claude in tmux",
                List.of("tmux", "new-window", "-c", "{dir}", "claude", "{prompt}"),
                false,
                Option.none(),
                Option.none());

        assertEquals(
                List.of("tmux", "new-window", "-c", "/repos/app", "claude", "Fix {dir} it"),
                agent.commandLine("/repos/app", "Fix {dir} it"));
    }

    @Test
    void withoutPromptThePromptIsTheLastArgument() {
        AgentProgram agent =
                new AgentProgram("Claude", List.of("claude", "--model", "x"), true, Option.none(), Option.none());

        assertEquals(List.of("claude", "--model", "x", "Fix it"), agent.commandLine("/repos/app", "Fix it"));
    }

    @Test
    void aBraceThatIsNoPlaceholderStaysAsItIs() {
        AgentProgram agent = new AgentProgram(
                "Agent",
                List.of("agent", "--json={\"a\": 1}", "{col}", "{prompt}"),
                true,
                Option.none(),
                Option.none());

        assertEquals(List.of("agent", "--json={\"a\": 1}", "{col}", "go"), agent.commandLine("/repos/app", "go"));
    }

    @Test
    void aPromptTemplateIsFilledInLeavingWhatIsNoPlaceholder() {
        assertEquals(
                "Fix main at abc; keep {\"x\": 1} and {unknown}",
                Programs.fill(
                        "Fix {branch} at {commit}; keep {\"x\": 1} and {unknown}",
                        LinkedHashMap.of("{branch}", "main", "{commit}", "abc")));
    }

    @Test
    void theAgentsAreReadInTheirOrderWithTheirPromptAndShortcut() {
        Agents.Read read = Agents.read(java.util.List.of(
                table("name", "Claude", "command", java.util.List.of("claude", "{prompt}"), "terminal", true),
                table(
                        "name",
                        "Codex",
                        "command",
                        java.util.List.of("codex"),
                        "shortcut",
                        "x",
                        "prompt",
                        "Look at {failures}")));

        assertEquals(List.empty(), read.invalid());
        assertEquals(
                List.of(
                        new AgentProgram("Claude", List.of("claude", "{prompt}"), true, Option.none(), Option.none()),
                        new AgentProgram(
                                "Codex", List.of("codex"), false, Option.of('x'), Option.of("Look at {failures}"))),
                read.agents().all());
        assertEquals(List.of('1', 'x'), read.agents().lettered().map(lettered -> lettered._1));
    }

    @Test
    void aWrongEntryIsLeftOutAndReportedTheOthersStay() {
        Agents.Read read = Agents.read(java.util.List.of(
                table("command", java.util.List.of("claude")),
                table("name", "Empty", "command", java.util.List.of()),
                table("name", "Bad", "command", java.util.List.of("x"), "terminal", "yes", "prompt", 3),
                table("name", "Good", "command", java.util.List.of("good"))));

        assertEquals(
                List.of("agents[1].name", "agents[2].command", "agents[3].terminal", "agents[3].prompt"),
                read.invalid());
        assertEquals(
                List.of(new AgentProgram("Good", List.of("good"), false, Option.none(), Option.none())),
                read.agents().all());
    }

    @Test
    void agentsThatAreNoArrayAreWrongAsAWhole() {
        Agents.Read read = Agents.read("claude");

        assertEquals(List.of("agents"), read.invalid());
        assertEquals(Agents.NONE, read.agents());
    }

    @Test
    void theAgentsAreReadFromTheSettings() {
        Settings.Read read = Settings.read(key -> key.equals("agents")
                ? Option.of(java.util.List.of(table("name", "Claude", "command", java.util.List.of("claude"))))
                : Option.none());

        assertEquals(
                List.of(Tuple.of(
                        '1', new AgentProgram("Claude", List.of("claude"), false, Option.none(), Option.none()))),
                read.settings().agents().lettered());
    }
}
