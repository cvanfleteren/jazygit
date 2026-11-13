package net.vanfleteren.jazygit.gh.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.gh.model.ChosenBrowser;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.gh.process.GhToken;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening a review comment in the browser gh would open it in, which jazygit starts itself (ADR 85).
 */
class BrowserTest {

    private static final PullRequestLink COMMENT = new PullRequestLink(
            "github.com", "owner/repo", new PrNumber(7), Option.of(new ReviewThread.Comment.RestId("42")));

    @TempDir
    Path dir;

    private static Option<String> chosen(Map<String, String> environment, Option<String> configured, String os) {
        return Browser.chosen(environment::get, () -> configured, os).map(ChosenBrowser::command);
    }

    @Test
    void aReviewCommentLinksToItsDiscussionInTheConversation() {
        assertEquals("https://github.com/owner/repo/pull/7#discussion_r42", COMMENT.url());
        assertEquals(
                "https://github.com/owner/repo/pull/7",
                new PullRequestLink("github.com", "owner/repo", new PrNumber(7), Option.none()).url());
    }

    @Test
    void theBrowserIsChosenInGhsOrder() {
        Map<String, String> both = HashMap.of("GH_BROWSER", "firefox", "BROWSER", "chromium");

        assertEquals(Option.of("firefox"), chosen(both, Option.of("brave"), "Linux"));
        assertEquals(Option.of("brave"), chosen(both.remove("GH_BROWSER"), Option.of("brave"), "Linux"));
        assertEquals(Option.of("chromium"), chosen(both.remove("GH_BROWSER"), Option.of("  \n"), "Linux"));
        assertEquals(Option.of("xdg-open"), chosen(HashMap.of("BROWSER", ""), Option.none(), "Linux"));
        assertEquals(Option.none(), chosen(HashMap.empty(), Option.none(), "Mac OS X"));
        assertEquals(Option.none(), chosen(HashMap.empty(), Option.none(), "Windows 11"));
        assertEquals(Option.of("chromium"), chosen(both.remove("GH_BROWSER"), Option.none(), "Windows 11"));
    }

    @Test
    void theBrowserIsSplitIntoWordsAsAShellSplitsThem() {
        assertEquals(List.of("firefox", "u"), Browser.commandLine("firefox", "u"));
        assertEquals(List.of("open", "-a", "Google Chrome", "u"), Browser.commandLine("open -a 'Google Chrome'", "u"));
        assertEquals(
                List.of("/opt/my browser/run", "--new-tab", "u"),
                Browser.commandLine("\"/opt/my browser/run\"  --new-tab ", "u"));
        assertEquals(List.of("/opt/my browser", "", "u"), Browser.commandLine("/opt/my\\ browser ''", "u"));
    }

    @Test
    void theBrowserIsGivenTheLinkWithItsAnchorAndNotWaitedFor() throws Exception {
        Path opened = dir.resolve("opened");
        Path browser = script("browser", "echo \"$@\" > '" + opened + "'\nsleep 5\n");

        Either<GhFailure, List<ExecutedCommand>> result =
                github(HashMap.of("BROWSER", browser + " --new-tab"), "Linux").openInBrowser(COMMENT);

        assertEquals(Either.right(List.of(ExecutedCommand.of(browser.toString(), "--new-tab", COMMENT.url()))), result);
        assertEquals("--new-tab " + COMMENT.url() + "\n", Files.readString(opened));
    }

    @Test
    void aBrowserThatFailsSaysWithWhatCode() throws Exception {
        Path browser = script("browser", "exit 4\n");

        Either<GhFailure, List<ExecutedCommand>> result =
                github(HashMap.of("BROWSER", browser.toString()), "Linux").openInBrowser(COMMENT);

        assertEquals(
                new GhError.BrowserFailed(browser.toString(), Option.of(4)),
                result.getLeft().error());
        assertEquals(
                List.of(ExecutedCommand.of(browser.toString(), COMMENT.url())),
                result.getLeft().commands());
    }

    @Test
    void ghsOwnBrowserSettingIsUsedForTheHost() throws Exception {
        Path opened = dir.resolve("opened");
        Path browser = script("browser", "echo \"$@\" > '" + opened + "'\n");
        Path gh = script("gh", "echo \"$*\" > '" + dir.resolve("asked") + "'\necho '" + browser + "'\n");

        assertTrue(CliGitHub.of(dir, gh.toString(), GhToken.NONE, HashMap.<String, String>empty()::get, "Linux")
                .account()
                .openInBrowser(COMMENT)
                .isRight());
        assertEquals("config get browser --host github.com\n", Files.readString(dir.resolve("asked")));
        assertEquals(COMMENT.url() + "\n", Files.readString(opened));
    }

    @Test
    void withoutABrowserSetOffLinuxGhOpensThePullRequest() throws Exception {
        assertEquals(
                Either.right(List.of(
                        ExecutedCommand.of("gh", "pr", "view", "https://github.com/owner/repo/pull/7", "--web"))),
                github(HashMap.empty(), "Mac OS X").openInBrowser(COMMENT));
        assertEquals(
                Either.right(List.of(ExecutedCommand.of("gh", "pr", "view", "7", "--web"))),
                github(HashMap.of("BROWSER", "firefox"), "Linux")
                        .openInBrowser(new PullRequestLink(
                                "", "owner/repo", new PrNumber(7), Option.of(new ReviewThread.Comment.RestId("42")))));
    }

    @Test
    void aLinkToAnywhereOpensInTheBrowserGhWouldChoose() throws Exception {
        Path opened = dir.resolve("opened");
        Path browser = script("browser", "echo \"$@\" > '" + opened + "'\n");

        Either<GhFailure, List<ExecutedCommand>> result =
                github(HashMap.of("BROWSER", browser.toString()), "Linux").openLink("https://example.com/a?b=c#d");

        assertEquals(
                Either.right(List.of(ExecutedCommand.of(browser.toString(), "https://example.com/a?b=c#d"))), result);
        assertEquals("https://example.com/a?b=c#d\n", Files.readString(opened));
    }

    @Test
    void aLinkToAnywhereAsksGhForTheBrowserOfTheRepositorysHost() throws Exception {
        Path opened = dir.resolve("opened");
        Path browser = script("browser", "echo \"$@\" > '" + opened + "'\n");
        Path gh = script("gh", "echo \"$*\" > '" + dir.resolve("asked") + "'\necho '" + browser + "'\n");

        assertTrue(CliGitHub.of(dir, gh.toString(), GhToken.NONE, HashMap.<String, String>empty()::get, "Linux")
                .account()
                .openLink("https://example.com")
                .isRight());
        assertEquals("config get browser --host github.com\n", Files.readString(dir.resolve("asked")));
        assertEquals("https://example.com\n", Files.readString(opened));
    }

    @Test
    void withoutABrowserSetALinkOpensWhereTheDesktopIsSetTo() {
        assertEquals(Option.of("xdg-open"), Browser.desktop("Linux"));
        assertEquals(Option.of("open"), Browser.desktop("Mac OS X"));
        assertEquals(Option.of("rundll32 url.dll,FileProtocolHandler"), Browser.desktop("Windows 11"));
        assertEquals(
                List.of("rundll32", "url.dll,FileProtocolHandler", "https://x.org/?a=1&b=2"),
                Browser.commandLine(Browser.desktop("Windows 11").get(), "https://x.org/?a=1&b=2"));
        assertEquals(Option.none(), Browser.desktop("FreeBSD"));
    }

    @Test
    void withoutABrowserSetOnASystemWithoutAKnownDesktopALinkCannotBeOpened() throws Exception {
        Either<GhFailure, List<ExecutedCommand>> result =
                github(HashMap.empty(), "FreeBSD").openLink("https://x.org");

        assertEquals(new GhError.NoBrowser(), result.getLeft().error());
        assertEquals(List.empty(), result.getLeft().commands());
    }

    // GitHub through a gh that says no browser is set and does nothing else, in that environment and on that system.
    private Account github(Map<String, String> environment, String os) throws Exception {
        return CliGitHub.of(dir, script("gh", "exit 0\n").toString(), GhToken.NONE, environment::get, os)
                .account();
    }

    private Path script(String name, String body) throws Exception {
        Path script = Files.writeString(dir.resolve(name), "#!/bin/sh\n" + body);
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }
}
