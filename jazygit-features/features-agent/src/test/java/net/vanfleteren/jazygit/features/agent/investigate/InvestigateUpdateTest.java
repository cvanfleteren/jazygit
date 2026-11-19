package net.vanfleteren.jazygit.features.agent.investigate;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.kernel.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.settings.Agents;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import org.junit.jupiter.api.Test;

/**
 * Handing a failed build to an agent the user set (ADR 161).
 */
class InvestigateUpdateTest {

    private static final BranchName MAIN_BRANCH = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName TAKEN = new BranchName("taken");
    private static final Path MAIN_PATH = Path.of("/repos/app");
    private static final Path LINKED_PATH = Path.of("/repos/app-taken");
    private static final Worktree MAIN = new Worktree(
            MAIN_PATH,
            Option.of(new Head.OnBranch(MAIN_BRANCH, Option.of(new Sha("aaaa")))),
            true,
            true,
            Option.none(),
            false);
    private static final Worktree LINKED = new Worktree(
            LINKED_PATH,
            Option.of(new Head.OnBranch(TAKEN, Option.of(new Sha("1111")))),
            false,
            false,
            Option.none(),
            false);
    private static final AgentProgram CLAUDE =
            new AgentProgram("Claude", List.of("claude", AgentProgram.PROMPT), true, Option.none(), Option.none());
    private static final AgentProgram CODEX =
            new AgentProgram("Codex", List.of("codex"), false, Option.of('x'), Option.of("Fix {branch}: {failures}"));

    private static final FailedBuild.Job VERIFY =
            new FailedBuild.Job("verify", Option.of("401"), Option.of("https://ci/401"));

    private static FailedBuild build(BranchName branch, List<FailedBuild.Job> jobs, boolean running) {
        return new FailedBuild(
                Option.of("o/r"),
                branch,
                Option.of("abc123"),
                Option.of(12),
                jobs,
                running,
                "gh run view 401 --log-failed --repo o/r");
    }

    private static FailedBuild failed(BranchName branch) {
        return build(branch, List.of(VERIFY), false);
    }

    /**
     * {@code main} is checked out in the main worktree, which is shown; {@code taken} in a linked one; {@code feature}
     * in none; with {@code agents} set.
     */
    private static Model model(List<AgentProgram> agents) {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(MAIN_BRANCH, true, new Sha("aaaa")),
                                Fixtures.branch(FEATURE, false, new Sha("ffff")),
                                new Branch(
                                        TAKEN,
                                        false,
                                        new Sha("1111"),
                                        Instant.EPOCH,
                                        Option.none(),
                                        false,
                                        false,
                                        new Tracking.None(),
                                        Option.of(LINKED_PATH))))
                .model();
        return Update.update(branches, new LoadMsg.WorktreesLoaded(List.of(MAIN, LINKED)))
                .model()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withAgents(new Agents(agents))));
    }

    @Test
    void withOneAgentItStartsAtOnceInTheWorktreeThatHasTheBranch() {
        Next next = Update.update(model(List.of(CLAUDE)), new InvestigateMsg.Requested(failed(TAKEN)));

        assertEquals(
                List.of(new InvestigateCmd(CLAUDE, LINKED_PATH.toString(), Prompt.of(failed(TAKEN), Option.none()))),
                next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void withMoreTheAgentsAreOfferedAndTheOneChosenStartsWithItsOwnPrompt() {
        Next offered = Update.update(model(List.of(CLAUDE, CODEX)), new InvestigateMsg.Requested(failed(MAIN_BRANCH)));

        assertEquals(List.empty(), offered.cmds());
        assertEquals(
                Option.of(new InvestigatePopup(
                        failed(MAIN_BRANCH),
                        MAIN_PATH.toString(),
                        List.of(Tuple.of('1', CLAUDE), Tuple.of('x', CODEX)))),
                offered.model().popup());

        Next chosen = Update.update(offered.model(), new InvestigateMsg.Chosen(CODEX));

        assertEquals(
                List.of(new InvestigateCmd(CODEX, MAIN_PATH.toString(), "Fix main: - verify (https://ci/401)")),
                chosen.cmds());
        assertEquals(Option.none(), chosen.model().popup());
    }

    @Test
    void withoutAnAgentSetItSaysHowToSetOne() {
        Next next = Update.update(model(List.empty()), new InvestigateMsg.Requested(failed(MAIN_BRANCH)));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of("No agent is set to investigate a failed build. Add one to the settings file, config.toml,"
                        + " such as:\n\n[[agents]]\nname = \"Claude\"\ncommand = [\"claude\", \"{prompt}\"]\n"
                        + "terminal = true"),
                errorText(next.model()));
    }

    @Test
    void aBranchNoWorktreeHasSaysSoRatherThanStartingElsewhere() {
        Next next = Update.update(model(List.of(CLAUDE)), new InvestigateMsg.Requested(failed(FEATURE)));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of("Branch feature is not checked out in a worktree"), errorText(next.model()));
    }

    @Test
    void withNothingFailedItSaysSoOrThatTheBuildStillRuns() {
        Next passed = Update.update(
                model(List.of(CLAUDE)), new InvestigateMsg.Requested(build(MAIN_BRANCH, List.empty(), false)));
        Next running = Update.update(
                model(List.of(CLAUDE)), new InvestigateMsg.Requested(build(MAIN_BRANCH, List.empty(), true)));

        assertEquals(List.empty(), passed.cmds());
        assertEquals(Option.of("Nothing failed in this build"), errorText(passed.model()));
        assertEquals(Option.of("Nothing failed yet: the build still runs"), errorText(running.model()));
    }

    @Test
    void withNothingHandedOverItSaysSo() {
        Next next = Update.update(model(List.of(CLAUDE)), new InvestigateMsg.Unavailable());

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of("Nothing to investigate yet: the build is still being read, or none is done"),
                errorText(next.model()));
    }

    @Test
    void beforeTheWorktreesAreReadItSaysSo() {
        Model unread = model(List.of(CLAUDE))
                .mapRepo(repo -> repo.withWorktrees(Loadable.loading()))
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withAgents(new Agents(List.of(CLAUDE)))));

        Next next = Update.update(unread, new InvestigateMsg.Requested(failed(MAIN_BRANCH)));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of("The worktrees are not read yet; try again in a moment"), errorText(next.model()));
    }

    @Test
    void jazygitsPromptSaysTheBuildIsOfTheBranchOnTheForgeEvenWithoutItsCommit() {
        FailedBuild tip =
                new FailedBuild(Option.of("o/r"), FEATURE, Option.none(), Option.none(), List.of(VERIFY), false, "");

        String prompt = Prompt.of(tip, Option.none());

        assertTrue(prompt.contains("may differ from what is checked out here"), prompt);
        assertTrue(prompt.contains("git log"), prompt);
    }

    @Test
    void oneFailedJobIsEnoughWhileOthersStillRun() {
        Next next = Update.update(
                model(List.of(CLAUDE)), new InvestigateMsg.Requested(build(MAIN_BRANCH, List.of(VERIFY), true)));

        assertEquals(1, next.cmds().size());
    }

    @Test
    void jazygitsPromptFencesWhatTheForgeGaveAndSaysTheCommitMayNotBeCheckedOut() {
        String prompt = Prompt.of(failed(MAIN_BRANCH), Option.none());

        assertTrue(prompt.contains("branch main of o/r (pull request #12)"), prompt);
        assertTrue(prompt.contains("may differ from what is checked out here"), prompt);
        assertTrue(
                prompt.contains("<forge-data>\nFailed jobs:\n- verify (https://ci/401)\n\nHow to read their logs:\n"
                        + "gh run view 401 --log-failed --repo o/r\n</forge-data>"),
                prompt);
    }

    @Test
    void aPromptOfTheUserLeavesWhatIsNoPlaceholderAndEmptiesWhatIsNotKnown() {
        FailedBuild unknown =
                new FailedBuild(Option.none(), FEATURE, Option.none(), Option.none(), List.of(VERIFY), false, "");

        assertEquals(
                "Fix feature in  at  (PR ) {\"x\": 1}",
                Prompt.of(unknown, Option.of("Fix {branch} in {repo} at {commit} (PR {pr}) {\"x\": 1}")));
    }
}
