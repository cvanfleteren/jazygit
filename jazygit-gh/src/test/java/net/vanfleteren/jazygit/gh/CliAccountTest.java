package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
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
                CliAccount.browseCommand(new GhBranch("feature/login")).commandLine("gh"));
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
    void ghRunsFromItsExecutableButIsLoggedAsGh() {
        GhCommand command = CliAccount.browseCommand(new GhBranch("main"));

        assertEquals("/opt/gh/bin/gh", command.commandLine("/opt/gh/bin/gh").head());
        assertEquals(
                List.of("gh", "browse", "--branch", "main"), command.executed().commandLine());
    }
}
