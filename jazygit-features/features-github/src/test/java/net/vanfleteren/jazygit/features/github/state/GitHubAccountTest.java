package net.vanfleteren.jazygit.features.github.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.account.StubAccount;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;
import org.junit.jupiter.api.Test;

/**
 * How a program jazygit starts is told to run gh as the account the repository uses (ADR 162).
 */
class GitHubAccountTest {

    @Test
    void onGitHubGhRunsAsTheAccountWithItsTokenForEachCommandWithoutSwitching() {
        assertEquals(
                new ForgeAccount(
                        "alice",
                        "github.com",
                        "put GH_TOKEN=\"$(gh auth token --user alice --hostname github.com)\" before each gh command,"
                                + " as in GH_TOKEN=\"$(gh auth token --user alice --hostname github.com)\" gh run view"
                                + " 401; if gh auth token gives nothing, stop and say so rather than running gh"
                                + " without it, which would run as the active account; never run gh auth switch, which"
                                + " changes the account of every program"),
                GitHubAccount.forge(new GhAccount("github.com", "alice")));
    }

    @Test
    void onAnEnterpriseServerItsHostGoesWithTheToken() {
        assertEquals(
                "put GH_ENTERPRISE_TOKEN=\"$(gh auth token --user bob --hostname ghe.corp)\" GH_HOST=ghe.corp before"
                        + " each gh command, as in GH_ENTERPRISE_TOKEN=\"$(gh auth token --user bob --hostname"
                        + " ghe.corp)\" GH_HOST=ghe.corp gh run view 401; if gh auth token gives nothing, stop and say"
                        + " so rather than running gh without it, which would run as the active account; never run gh"
                        + " auth switch, which changes the account of every program",
                GitHubAccount.forge(new GhAccount("ghe.corp", "bob")).runAs());
    }

    @Test
    void theForgeAccountIsTheOneConfiguredForTheRepositoryNotGhsActiveOne() {
        GhAccount alice = new GhAccount("ghe.corp", "alice");
        GhAccount active = new GhAccount("github.com", "someone-else");

        assertEquals(
                Option.of(GitHubAccount.forge(alice)),
                GitHubAccount.ON_FORGE.of(new Env(StubGit.of(), gitHub(Option.of(alice), Option.of(alice)))));
        assertEquals(
                Option.none(),
                GitHubAccount.ON_FORGE.of(new Env(StubGit.of(), gitHub(Option.none(), Option.of(active)))));
    }

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
}
