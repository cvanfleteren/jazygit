package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import org.junit.jupiter.api.Test;

/**
 * The accounts gh is logged in to, from what {@code gh auth status --json hosts} prints.
 */
class LoginsQueryTest {

    @Test
    void theLoginsOnEachHostInTheOrderGhGivesThem() {
        assertEquals(
                Either.right(List.of(
                        new GhLogin("github.com", "alice", true, true),
                        new GhLogin("github.com", "alice-work", false, false),
                        new GhLogin("ghe.corp", "alice", true, true))),
                GhProcess.decoded("""
                        {"hosts": {
                          "github.com": [
                            {"state": "success", "active": true, "host": "github.com", "login": "alice",
                             "tokenSource": "keyring", "scopes": "repo", "gitProtocol": "ssh"},
                            {"state": "error", "active": false, "host": "github.com", "login": "alice-work",
                             "tokenSource": "keyring", "gitProtocol": "ssh"}],
                          "ghe.corp": [
                            {"state": "success", "active": true, "host": "ghe.corp", "login": "alice",
                             "tokenSource": "GH_TOKEN", "gitProtocol": "https"}]}}
                        """, LoginsQuery.DECODER));
    }

    @Test
    void noHostsIsNoLogins() {
        assertEquals(Either.right(List.empty()), GhProcess.decoded("{\"hosts\": {}}", LoginsQuery.DECODER));
    }

    @Test
    void aLoginWithoutItsStateIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("hosts.github.com[0].state: missing")),
                GhProcess.decoded("""
                        {"hosts": {"github.com": [{"active": true, "host": "github.com", "login": "alice"}]}}
                        """, LoginsQuery.DECODER));
    }

    @Test
    void theLoginsAreAskedWithoutJq() {
        assertEquals(
                List.of("gh", "auth", "status", "--json", "hosts"),
                LoginsQuery.COMMAND.executed().commandLine());
    }
}
