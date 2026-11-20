package net.vanfleteren.jazygit.features.agent.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.Kept;
import net.vanfleteren.jazygit.kernel.settings.Programs;
import org.junit.jupiter.api.Test;

/**
 * The agents of the settings file, which a failed build is handed to (ADR 161), and which the panel writes back
 * (ADR 164).
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

    /**
     * The agents the settings file holds as {@code tables}, with the keys of what is wrong in them.
     */
    private static Decoded<Entries<AgentProgram>> read(Object tables) {
        return Agents.SETTING.codec().decode(Agents.KEY, tables);
    }

    @Test
    void theAgentsAreReadInTheirOrderWithTheirPromptAndShortcut() {
        Decoded<Entries<AgentProgram>> read = read(java.util.List.of(
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
        Agents agents = Agents.of(read.value().get());
        assertEquals(
                List.of(
                        new AgentProgram("Claude", List.of("claude", "{prompt}"), true, Option.none(), Option.none()),
                        new AgentProgram(
                                "Codex", List.of("codex"), false, Option.of('x'), Option.of("Look at {failures}"))),
                agents.all());
        assertEquals(List.of('1', 'x'), agents.lettered().map(lettered -> lettered._1));
    }

    @Test
    void aWrongEntryIsLeftOutAndReportedTheOthersStay() {
        Decoded<Entries<AgentProgram>> read = read(java.util.List.of(
                table("command", java.util.List.of("claude")),
                table("name", "Empty", "command", java.util.List.of()),
                table("name", "Bad", "command", java.util.List.of("x"), "terminal", "yes", "prompt", 3),
                table("name", "Good", "command", java.util.List.of("good"))));

        assertEquals(
                List.of("agents[1].name", "agents[2].command", "agents[3].terminal", "agents[3].prompt"),
                read.invalid());
        assertEquals(
                List.of(new AgentProgram("Good", List.of("good"), false, Option.none(), Option.none())),
                Agents.of(read.value().get()).all());
        // The ones that are wrong are kept, to be written back as they were.
        assertEquals(4, read.value().get().all().size());
    }

    @Test
    void agentsThatAreNoArrayAreWrongAsAWhole() {
        Decoded<Entries<AgentProgram>> read = read("claude");

        assertEquals(List.of("agents"), read.invalid());
        assertEquals(Option.none(), read.value());
    }

    @Test
    void aShortcutAnEarlierAgentHasIsReportedAndNotUsed() {
        Decoded<Entries<AgentProgram>> read = read(java.util.List.of(
                table("name", "Claude", "command", java.util.List.of("claude"), "shortcut", "c"),
                table("name", "Codex", "command", java.util.List.of("codex"), "shortcut", "c")));

        assertEquals(List.of("agents[2].shortcut"), read.invalid());
        assertEquals(
                List.of(Option.of('c'), Option.none()),
                Agents.of(read.value().get()).all().map(AgentProgram::shortcut));
    }

    @Test
    void aShortcutThatIsNoLetterIsReportedAndKeptInTheFile() {
        Decoded<Entries<AgentProgram>> read = read(
                java.util.List.of(table("name", "Claude", "command", java.util.List.of("claude"), "shortcut", "/")));

        assertEquals(List.of("agents[1].shortcut"), read.invalid());
        assertEquals(
                java.util.List.of(table("name", "Claude", "command", java.util.List.of("claude"), "shortcut", "/")),
                Agents.SETTING
                        .codec()
                        .encode(Agents.KEY, read.value().get())
                        .get(List.of(Agents.KEY))
                        .get());
    }

    @Test
    void anAgentIsKeptWithWhatItSetsAndWhatItsTableHadBesides() {
        Entries<AgentProgram> read = read(java.util.List.of(table(
                        "name", "Claude", "command", java.util.List.of("claude"), "terminal", false, "model", "opus")))
                .value()
                .get();
        Entries<AgentProgram> edited = read.replaced(
                0,
                new AgentProgram("Claude", List.of("claude", "-p"), true, Option.of('c'), Option.of("Fix {failures}")));

        assertEquals(
                java.util.List.of(table(
                        "name",
                        "Claude",
                        "command",
                        java.util.List.of("claude", "-p"),
                        "terminal",
                        true,
                        "shortcut",
                        "c",
                        "prompt",
                        "Fix {failures}",
                        "model",
                        "opus")),
                Agents.SETTING
                        .codec()
                        .encode(Agents.KEY, edited)
                        .get(List.of(Agents.KEY))
                        .get());
    }

    @Test
    void theAppWritesTheAgentsOnceTheyChanged() {
        assertEquals(Kept.WHEN_CHANGED, Agents.SETTING.kept());
        assertEquals(Entries.none(), Agents.SETTING.initial());
        assertTrue(Agents.SETTING.codec().guarded());
    }
}
