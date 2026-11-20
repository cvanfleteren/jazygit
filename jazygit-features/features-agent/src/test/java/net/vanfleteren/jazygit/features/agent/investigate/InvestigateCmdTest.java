package net.vanfleteren.jazygit.features.agent.investigate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.account.StubAccount;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.StubEditor;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.github.GitHubAccount;
import org.junit.jupiter.api.Test;

/**
 * What starting the agent tells it of the account the repository uses on its forge (ADR 162).
 */
class InvestigateCmdTest {

    private static final GhAccount ALICE = new GhAccount("ghe.corp", "alice");
    private static final GhAccount ACTIVE = new GhAccount("github.com", "someone-else");

    // An agent left running, asked only which account to use, so the command line shows just that.
    private static final AgentProgram AGENT = new AgentProgram(
            "Agent", List.of("agent", AgentProgram.PROMPT), false, Option.none(), Option.of("{account}"));

    private static final FailedBuild BUILD = new FailedBuild(
            Option.of("o/r"),
            new BranchName("main"),
            Option.none(),
            Option.none(),
            List.of(new FailedBuild.Job("verify", Option.none())),
            false,
            "read the log of run 401");

    private static GitHub gitHub(Option<GhAccount> configured, Option<GhAccount> current) {
        return StubGitHub.of().withAccount(new StubAccount() {
            @Override
            public Option<GhAccount> configured() {
                return configured;
            }

            @Override
            public Option<GhAccount> current() {
                return current;
            }
        });
    }

    private static java.util.List<List<String>> launched(GitHub gitHub) {
        java.util.List<List<String>> launched = new ArrayList<>();
        Env env = new Env(
                StubGit.of().withEditor(new StubEditor() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> launch(Path directory, List<String> commandLine) {
                        launched.add(commandLine);
                        return Either.right(List.of(new ExecutedCommand(commandLine)));
                    }
                }),
                gitHub);
        new InvestigateCmd(AGENT, "/repos/app", BUILD).run(env);
        return launched;
    }

    @Test
    void theAgentIsToldTheAccountConfiguredForTheRepository() {
        GitHub gitHub = gitHub(Option.of(ALICE), Option.of(ALICE));

        assertEquals(Option.of(GitHubAccount.forge(ALICE)), new Env(StubGit.of(), gitHub).forgeAccount());
        assertEquals(java.util.List.of(List.of("agent", Prompt.account(GitHubAccount.forge(ALICE)))), launched(gitHub));
    }

    @Test
    void ghsActiveAccountIsNotToldAsTheRepositorysOwn() {
        GitHub gitHub = gitHub(Option.none(), Option.of(ACTIVE));

        assertEquals(Option.none(), new Env(StubGit.of(), gitHub).forgeAccount());
        assertEquals(java.util.List.of(List.of("agent", "")), launched(gitHub));
    }
}
