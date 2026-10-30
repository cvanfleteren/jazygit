package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Running gh as the account configured for a repository, with a script standing in for gh that writes down how
 * it was called.
 */
@DisabledOnOs(OS.WINDOWS)
class GhTokenTest {

    private static final GhAccount ALICE = new GhAccount("github.com", "alice-work");

    @TempDir
    Path dir;

    /**
     * A gh that writes its arguments and its {@code GH_TOKEN} to {@code calls}, and runs {@code body}.
     */
    private String gh(String body) throws IOException {
        Path script = dir.resolve("gh");
        Files.writeString(script, """
                #!/bin/sh
                echo "$* token=$GH_TOKEN" >> "%s"
                %s
                """.formatted(calls(), body));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script.toString();
    }

    private Path calls() {
        return dir.resolve("calls");
    }

    private List<String> called() throws IOException {
        return Files.exists(calls()) ? List.ofAll(Files.readAllLines(calls())) : List.empty();
    }

    @Test
    void withoutAnAccountGhIsNotAsked() throws IOException {
        GhToken token = new GhToken(dir, gh("echo nope"), Option::none);

        assertEquals(Either.right(HashMap.empty()), token.environment());
        assertEquals(List.empty(), called());
    }

    @Test
    void theTokenOfTheAccountIsAskedOnceAndKept() throws IOException {
        GhToken token = new GhToken(dir, gh("echo secret"), () -> Option.of(ALICE));

        assertEquals(Either.right(HashMap.of("GH_TOKEN", "secret")), token.environment());
        assertEquals(Either.right(HashMap.of("GH_TOKEN", "secret")), token.environment());
        assertEquals(List.of("auth token --user alice-work --hostname github.com token="), called());
    }

    @Test
    void aTokenThatCannotBeHadIsKeptAsSuch() throws IOException {
        GhToken token = new GhToken(
                dir,
                gh("echo 'no oauth token found for github.com account alice-work' >&2; exit 1"),
                () -> Option.of(ALICE));

        GhError error = token.environment().getLeft();

        GhError.TokenUnavailable unavailable = assertInstanceOf(GhError.TokenUnavailable.class, error);
        assertEquals(ALICE, unavailable.account());
        assertEquals(
                new GhError.CommandFailed(Option.of("no oauth token found for github.com account alice-work"), 1),
                unavailable.cause());
        token.environment();
        assertEquals(1, called().size());
    }

    @Test
    void forgettingTheTokenAsksItAgain() throws IOException {
        GhToken token = new GhToken(dir, gh("echo secret"), () -> Option.of(ALICE));
        token.environment();

        token.forget();
        token.environment();

        assertEquals(2, called().size());
    }

    @Test
    void ghRunsWithTheTokenOfTheAccount() throws IOException {
        String gh = gh("echo secret");
        GhToken token = new GhToken(dir, gh, () -> Option.of(ALICE));

        new GhProcess(dir, gh, token).log(GhCommand.gh("pr", "close", "1")).get();

        assertEquals("pr close 1 token=secret", called().last());
    }

    @Test
    void aRejectedTokenIsAskedAgainOnce() throws IOException {
        // The first pr command is rejected, as the token was replaced since it was asked.
        String gh = gh("""
                case "$1" in
                  auth) echo "token$(grep -c '^auth' "%s")" ;;
                  pr) [ "$GH_TOKEN" = token2 ] || { echo 'HTTP 401: Bad credentials (https://api.github.com/graphql)' >&2; exit 1; } ;;
                esac
                """.formatted(calls()));
        GhToken token = new GhToken(dir, gh, () -> Option.of(ALICE));

        new GhProcess(dir, gh, token).log(GhCommand.gh("pr", "close", "1")).get();

        assertEquals(
                List.of(
                        "auth token --user alice-work --hostname github.com token=",
                        "pr close 1 token=token1",
                        "auth token --user alice-work --hostname github.com token=",
                        "pr close 1 token=token2"),
                called());
    }

    @Test
    void aCommandThatFailsOtherwiseIsNotRunAgain() throws IOException {
        // Running it again would merge, create or comment twice when it did so before failing.
        String gh = gh("""
                case "$1" in
                  auth) echo secret ;;
                  pr) echo 'GraphQL: Pull request is not mergeable' >&2; exit 1 ;;
                esac
                """);
        GhToken token = new GhToken(dir, gh, () -> Option.of(ALICE));

        GhFailure failure = new GhProcess(dir, gh, token)
                .log(GhCommand.gh("pr", "merge", "1"))
                .getLeft();

        assertEquals(
                List.of("auth token --user alice-work --hostname github.com token=", "pr merge 1 token=secret"),
                called());
        assertEquals(
                new GhError.CommandFailed(Option.of("GraphQL: Pull request is not mergeable"), 1), failure.error());
    }

    @Test
    void withoutAnAccountARejectedCommandIsNotRunAgain() throws IOException {
        String gh = gh("echo 'HTTP 401: Bad credentials' >&2; exit 1");

        new GhProcess(dir, gh, GhToken.NONE).log(GhCommand.gh("pr", "close", "1"));

        assertEquals(1, called().size());
    }

    @Test
    void anEnterpriseServerGetsItsOwnToken() {
        assertEquals(HashMap.of("GH_TOKEN", "t"), GhToken.variables("github.com", "t"));
        assertEquals(
                HashMap.of("GH_ENTERPRISE_TOKEN", "t", "GH_HOST", "github.corp.example"),
                GhToken.variables("github.corp.example", "t"));
    }

    @Test
    void theHostNameOfAnSshAlias() {
        assertEquals(Option.of("github.com"), GhToken.sshHostName("user git\nhostname github.com\nport 22\n"));
        assertEquals(Option.none(), GhToken.sshHostName("user git\n"));
    }
}
