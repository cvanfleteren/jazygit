package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which GitHub host a repository is on, by its remotes.
 */
class GhAccountTest {

    @Test
    void theHostOfAnHttpsUrl() {
        assertEquals(Option.of("github.com"), GhAccount.hostOf("https://github.com/owner/repo.git"));
        assertEquals(Option.of("github.example.com"), GhAccount.hostOf("https://me@github.example.com:8443/owner/repo"));
    }

    @Test
    void theHostOfAnSshUrl() {
        assertEquals(Option.of("github.com"), GhAccount.hostOf("ssh://git@github.com/owner/repo.git"));
        assertEquals(Option.of("github.com"), GhAccount.hostOf("git@github.com:owner/repo.git"));
        assertEquals(Option.of("github-work"), GhAccount.hostOf("github-work:owner/repo.git"));
    }

    @Test
    void aLocalPathHasNoHost() {
        assertEquals(Option.none(), GhAccount.hostOf("/srv/git/repo.git"));
        assertEquals(Option.none(), GhAccount.hostOf("C:\\\\repos\\\\repo.git"));
        assertEquals(Option.none(), GhAccount.hostOf("../repo.git"));
    }

    @Test
    void originsHostComesFirst() {
        assertEquals(Option.of("git.corp.example"), GhAccount.remoteHost(HashMap.of(
                "origin", "git@git.corp.example:team/repo.git",
                "github", "git@github.com:team/repo.git")));
    }

    @Test
    void withoutOriginTheFirstRemoteOnAGitHubHost() {
        assertEquals(Option.of("github.com"), GhAccount.remoteHost(HashMap.of(
                "upstream", "git@github.com:team/repo.git",
                "mirror", "https://gitlab.com/team/repo.git")));
        assertEquals(Option.none(), GhAccount.remoteHost(HashMap.of("mirror", "https://gitlab.com/team/repo.git")));
    }

    @Test
    void gitHubItselfIsGitHubComAndItsDataResidencyHosts() {
        assertTrue(GhAccount.isGitHubCom("github.com"));
        assertTrue(GhAccount.isGitHubCom("acme.ghe.com"));
        assertFalse(GhAccount.isGitHubCom("github.corp.example"));
    }
}
