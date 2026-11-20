package net.vanfleteren.jazygit.features.agent.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.settings.Field;
import net.vanfleteren.jazygit.kernel.state.settings.FieldProblem;
import org.junit.jupiter.api.Test;

/**
 * The form of an agent in the settings panel (ADR 164): what it shows of an agent, how what is typed changes it, and
 * what keeps it from being saved.
 */
class AgentFormTest {

    private static final AgentProgram CLAUDE =
            new AgentProgram("Claude", List.of("claude", "{prompt}"), true, Option.of('c'), Option.none());
    private static final AgentProgram CODEX =
            new AgentProgram("Codex", List.of("codex"), false, Option.of('x'), Option.none());

    private static List<FieldProblem> problems(AgentProgram agent, AgentProgram... others) {
        return AgentForm.FORM.problems(agent, List.of(others));
    }

    @SuppressWarnings("unchecked")
    private static Field.Text<AgentProgram> text(String labelKey) {
        return (Field.Text<AgentProgram>) AgentForm.FORM
                .fields()
                .find(field -> field.labelKey().equals(labelKey))
                .get();
    }

    @Test
    void anAgentWithANameACommandAndAFreeShortcutHasNothingWrong() {
        assertEquals(List.empty(), problems(CLAUDE, CODEX));
        assertTrue(AgentForm.FORM.savable(CLAUDE, List.of(CODEX)));
    }

    @Test
    void aNewAgentNeedsANameAndACommand() {
        assertEquals(
                List.of(
                        FieldProblem.error(AgentForm.NAME, "agent.settings.problem.empty"),
                        FieldProblem.error(AgentForm.COMMAND, "agent.settings.problem.empty")),
                problems(AgentForm.BLANK));
        assertFalse(AgentForm.FORM.savable(AgentForm.BLANK, List.empty()));
    }

    @Test
    void aNameOrAShortcutAnotherAgentHasIsNotSaved() {
        AgentProgram twin = CODEX.withName("Claude").withShortcut(Option.of('c'));

        assertEquals(
                List.of(
                        FieldProblem.error(AgentForm.NAME, "agent.settings.problem.nameTaken"),
                        FieldProblem.error(AgentForm.SHORTCUT, "agent.settings.problem.shortcutTaken", "Claude")),
                problems(twin, CLAUDE));
    }

    @Test
    void theFilterKeyIsNoShortcut() {
        assertEquals(
                List.of(FieldProblem.error(AgentForm.SHORTCUT, "agent.settings.problem.shortcut", "/")),
                problems(CLAUDE.withShortcut(Option.of('/'))));
    }

    @Test
    void aPlaceholderJazygitDoesNotFillOnlyWarns() {
        AgentProgram agent = CLAUDE.withPrompt(Option.of("Fix {failures} on {brnach}, not {\"a\": 1}"));

        List<FieldProblem> problems = problems(agent);

        assertEquals(
                List.of(FieldProblem.warning(
                        AgentForm.PROMPT,
                        "agent.settings.problem.placeholder",
                        "{brnach}",
                        "{repo}, {branch}, {commit}, {pr}, {failures}, {logs}, {account}")),
                problems);
        assertTrue(AgentForm.FORM.savable(agent, List.empty()));
    }

    @Test
    void theCommandIsEditedAsTheLineAShellSplits() {
        Field.Text<AgentProgram> command = text(AgentForm.COMMAND);
        AgentProgram typed = command.set().apply(CLAUDE, "claude --append-system-prompt 'be brief' {prompt}");

        assertEquals(List.of("claude", "--append-system-prompt", "be brief", "{prompt}"), typed.command());
        assertEquals(
                "claude --append-system-prompt 'be brief' '{prompt}'",
                command.get().apply(typed));
        assertEquals(
                typed.command(),
                command.set().apply(CLAUDE, command.get().apply(typed)).command());
    }

    @Test
    void anEmptyPromptIsJazygitsOwn() {
        Field.Text<AgentProgram> prompt = text(AgentForm.PROMPT);

        assertEquals(
                Option.none(),
                prompt.set().apply(CLAUDE.withPrompt(Option.of("x")), "  ").prompt());
        assertEquals("", prompt.get().apply(CLAUDE));
    }
}
