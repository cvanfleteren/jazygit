package net.vanfleteren.jazygit.features.agent.investigate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramFailure;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramLauncher;
import org.junit.jupiter.api.Test;

/**
 * What starting the agent tells it of the account the repository uses on its forge (ADR 162).
 */
class InvestigateCmdTest {

    private static final ForgeAccount ALICE = new ForgeAccount("alice", "ghe.corp", "run gh as alice");

    // An agent left running, asked only which account to use, so the command line shows just that.
    private static final AgentProgram AGENT = new AgentProgram(
            "Agent", List.of("agent", AgentProgram.PROMPT), false, Option.none(), Option.of("{account}"));

    private static final FailedBuild BUILD = new FailedBuild(
            Option.of(new RepoSlug("o/r")),
            new BranchName("main"),
            Option.none(),
            Option.none(),
            List.of(new FailedBuild.Job("verify", Option.none())),
            false,
            "read the log of run 401");

    private static java.util.List<List<String>> launched(Option<ForgeAccount> account) {
        java.util.List<List<String>> launched = new ArrayList<>();
        Env env = new Env(StubGit.of())
                .with(new ProgramLauncher() {
                    @Override
                    public Either<ProgramFailure, ExecutedCommand> run(Path directory, List<String> commandLine) {
                        throw new AssertionError("an agent left running is not handed the terminal");
                    }

                    @Override
                    public Either<ProgramFailure, ExecutedCommand> start(Path directory, List<String> commandLine) {
                        launched.add(commandLine);
                        return Either.right(new ExecutedCommand(commandLine));
                    }
                })
                .with(Env.FORGE_ACCOUNT, any -> account);
        new InvestigateCmd(AGENT, "/repos/app", BUILD).run(env);
        return launched;
    }

    @Test
    void theAgentIsToldTheAccountTheForgeGives() {
        assertEquals(java.util.List.of(List.of("agent", Prompt.account(ALICE))), launched(Option.of(ALICE)));
    }

    @Test
    void withoutOneTheAgentIsToldNone() {
        assertEquals(java.util.List.of(List.of("agent", "")), launched(Option.none()));
    }
}
