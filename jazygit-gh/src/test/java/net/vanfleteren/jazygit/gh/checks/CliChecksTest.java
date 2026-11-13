package net.vanfleteren.jazygit.gh.checks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
import org.junit.jupiter.api.Test;

/**
 * The checks of the branches through gh (ADR 114).
 */
class CliChecksTest {

    @Test
    void aRemotePointsAtTheRepositoryOverSshOrHttps() {
        BranchChecks checks = new BranchChecks("Owner/repo", HashMap.empty());

        assertTrue(checks.isAt("git@github.com:owner/repo.git"));
        assertTrue(checks.isAt("https://github.com/Owner/repo"));
        assertTrue(checks.isAt("https://github.com/owner/repo.git/"));
        assertTrue(checks.isAt("ssh://git@github.com/owner/repo.git"));
        assertTrue(checks.isAt("github-work:owner/repo.git"));
        assertFalse(checks.isAt("git@github.com:fork/repo.git"));
        assertFalse(checks.isAt("git@github.com:owner/repository.git"));
        assertFalse(checks.isAt("git@github.com:other-owner/repo.git"));
    }

    @Test
    void theSuitesOfACommitAreReadWhereverItsChecksAre() {
        // Without them, a workflow that waits to start reads as no checks (ADR 35).
        List.of(BranchesQuery.QUERY, BranchCheckRunsQuery.query(1))
                .forEach(query -> assertTrue(query.contains("checkSuites(first: 20)"), query));
    }
}
