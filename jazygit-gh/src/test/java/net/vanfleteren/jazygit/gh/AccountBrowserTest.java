package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening links in the browser set for the account a repository uses (ADR 88), with a script standing in for gh
 * whose active account is {@code cvf}.
 */
@DisabledOnOs(OS.WINDOWS)
class AccountBrowserTest {

    private static final PullRequestLink COMMENT =
            new PullRequestLink("github.com", "owner/repo", 7, Option.of(new ReviewThread.Comment.RestId("42")));
    private static final GhAccount ACTIVE = new GhAccount("github.com", "cvf");

    @TempDir
    Path dir;

    @Test
    void theAccountIsGhsActiveOneWhenTheRepositoryIsConfiguredToUseNone() throws Exception {
        assertEquals(
                Option.of(ACTIVE),
                github(GhToken.NONE, HashMap.empty(), account -> Option.none()).account());
        assertEquals("config get user -h github.com\n", Files.readString(dir.resolve("asked")));
    }

    @Test
    void theAccountIsTheConfiguredOneWithoutAskingGh() throws Exception {
        GhAccount work = new GhAccount("github.com", "cvf-work");
        GhToken token = new GhToken(dir, "gh", () -> Option.of(work));

        assertEquals(
                Option.of(work),
                github(token, HashMap.empty(), account -> Option.none()).account());
        assertTrue(Files.notExists(dir.resolve("asked")));
    }

    @Test
    void underATokenInTheEnvironmentTheActiveAccountIsNotKnown() throws Exception {
        assertEquals(
                Option.none(),
                github(GhToken.NONE, HashMap.of("GH_TOKEN", "t"), account -> Option.none())
                        .account());
        assertEquals(
                Option.none(),
                github(GhToken.NONE, HashMap.of("GITHUB_TOKEN", "t"), account -> Option.none())
                        .account());
        // A token for an enterprise server does not change who gh is on github.com.
        assertEquals(
                Option.of(ACTIVE),
                github(GhToken.NONE, HashMap.of("GH_ENTERPRISE_TOKEN", "t"), account -> Option.none())
                        .account());
    }

    @Test
    void theAccountsBrowserComesBeforeGhsOrder() throws Exception {
        Path opened = dir.resolve("opened");
        Path browser = script("work-browser", "echo \"$@\" > '" + opened + "'\n");

        Either<GhFailure, List<ExecutedCommand>> result = github(
                        GhToken.NONE, HashMap.of("GH_BROWSER", "elsewhere"), browserOf(ACTIVE, browser + " -P work"))
                .openInBrowser(COMMENT);

        assertEquals(
                Either.right(List.of(ExecutedCommand.of(browser.toString(), "-P", "work", COMMENT.url()))), result);
        assertEquals("-P work " + COMMENT.url() + "\n", Files.readString(opened));
    }

    @Test
    void aLinkToAnywhereOpensInTheAccountsBrowserToo() throws Exception {
        Path opened = dir.resolve("opened");
        Path browser = script("work-browser", "echo \"$@\" > '" + opened + "'\n");

        Either<GhFailure, List<ExecutedCommand>> result = github(
                        GhToken.NONE, HashMap.of("BROWSER", "elsewhere"), browserOf(ACTIVE, browser + " -P work"))
                .openLink("https://example.com");

        assertEquals(
                Either.right(List.of(ExecutedCommand.of(browser.toString(), "-P", "work", "https://example.com"))),
                result);
        assertEquals("-P work https://example.com\n", Files.readString(opened));
    }

    @Test
    void anAccountsBrowserThatFailsIsNotFollowedByAnother() throws Exception {
        Path browser = script("work-browser", "exit 4\n");

        Either<GhFailure, List<ExecutedCommand>> result = github(
                        GhToken.NONE, HashMap.of("BROWSER", "elsewhere"), browserOf(ACTIVE, browser.toString()))
                .openInBrowser(COMMENT);

        assertEquals(
                new GhError.BrowserFailed(browser.toString(), Option.of(4)),
                result.getLeft().error());
    }

    @Test
    void ghOpensInTheAccountsBrowserToo() throws Exception {
        CliGitHub github = github(GhToken.NONE, HashMap.empty(), browserOf(ACTIVE, "firefox -P work"));

        assertTrue(github.openPullRequest(7).isRight());
        assertEquals("firefox -P work\n", Files.readString(dir.resolve("browser")));
        assertTrue(github.browse(new GhBranch("feature")).isRight());
        assertEquals("firefox -P work\n", Files.readString(dir.resolve("browser")));
    }

    @Test
    void withoutABrowserForTheAccountGhChoosesAsBefore() throws Exception {
        assertTrue(github(GhToken.NONE, HashMap.empty(), account -> Option.none())
                .openPullRequest(7)
                .isRight());
        assertEquals("unset\n", Files.readString(dir.resolve("browser")));
    }

    private static java.util.function.Function<GhAccount, Option<String>> browserOf(GhAccount owner, String browser) {
        return account -> Option.when(account.equals(owner), browser);
    }

    /**
     * GitHub through a gh whose active account on github.com is {@code cvf}: it writes down what it is asked to read
     * in {@code asked}, and the {@code GH_BROWSER} it opens something with in {@code browser}.
     */
    private CliGitHub github(
            GhToken token,
            Map<String, String> environment,
            java.util.function.Function<GhAccount, Option<String>> browsers)
            throws Exception {
        Path gh = script("gh", """
                case "$1 $2" in
                  "config get")
                    echo "$*" >> '%s'
                    if [ "$3" = user ]; then echo cvf; fi
                    ;;
                  *)
                    echo "${GH_BROWSER-unset}" > '%s'
                    ;;
                esac
                """.formatted(dir.resolve("asked"), dir.resolve("browser")));
        return new CliGitHub(dir, gh.toString(), token, () -> GhAccount.GITHUB, browsers, environment::get, "Linux");
    }

    private Path script(String name, String body) throws Exception {
        Path script = Files.writeString(dir.resolve(name), "#!/bin/sh\n" + body);
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }
}
