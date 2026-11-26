package net.vanfleteren.jazygit.core.gh.account;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.*;
import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhProcess;
import net.vanfleteren.jazygit.core.gh.process.GhToken;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import org.junit.jupiter.api.Test;

/**
 * The gh that runs and what it opens in the browser (ADR 114); the browser of an account is in
 * {@link AccountBrowserTest}.
 */
class CliAccountTest {

    @Test
    void browsingOpensTheBranchByItsNameOnTheRemote() {
        assertEquals(
                List.of("gh", "browse", "--branch", "feature/login"),
                CliAccount.browseCommand(new ForgeBranch("feature/login")).commandLine("gh"));
    }

    @Test
    void theVersionIsInTheFirstLine() {
        assertEquals(Either.right("2.102.0"), CliAccount.parseVersion("""
                gh version 2.102.0 (2026-09-30)
                https://github.com/cli/cli/releases/tag/v2.102.0
                """));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("Usage: something else")),
                CliAccount.parseVersion("Usage: something else"));
    }

    @Test
    void theAccountConfiguredIsOnlyTheOneOfTheRepositoryNotGhsActiveOne() {
        Path here = Path.of(".");
        GhAccount alice = new GhAccount("github.com", "alice");
        CliAccount configured = account(here, new GhToken(here, "gh", () -> Option.of(alice)));
        CliAccount none = account(here, GhToken.NONE);

        assertEquals(Option.of(alice), configured.configured());
        assertEquals(Option.none(), none.configured());
    }

    private static CliAccount account(Path worktree, GhToken token) {
        GhProcess process = new GhProcess(worktree, "gh", token);
        return new CliAccount(
                worktree,
                process,
                new GhProcess(worktree, "gh", GhToken.NONE),
                token,
                () -> "github.com",
                account -> Option.none(),
                variable -> Option.none(),
                "Linux");
    }

    @Test
    void ghRunsFromItsExecutableButIsLoggedAsGh() {
        GhCommand command = CliAccount.browseCommand(new ForgeBranch("main"));

        assertEquals("/opt/gh/bin/gh", command.commandLine("/opt/gh/bin/gh").head());
        assertEquals(
                List.of("gh", "browse", "--branch", "main"), command.executed().commandLine());
    }
}
