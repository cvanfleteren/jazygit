package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.RAN_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.control.Either;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The branches of the repository with the state of the checks on their tip, from all pages GitHub answers.
 */
class BranchesQueryTest {

    private static Either<GhError.UnexpectedOutput, BranchChecks> branches(String json) {
        return GhProcess.decoded(json, BranchesQuery.DECODER);
    }

    private static String page(String nodes) {
        return """
                { "data": { "repository": { "nameWithOwner": "owner/repo", "refs": {
                  "pageInfo": { "hasNextPage": false, "endCursor": null }, "nodes": [ %s ] } } } }""".formatted(nodes);
    }

    private static String branch(String name, String rollup, String suites) {
        return """
                { "name": "%s", "target": { "__typename": "Commit", "statusCheckRollup": %s, %s } }""".formatted(name, rollup, suites);
    }

    @Test
    void theRepositoryAndTheChecksOfEachBranchOverAllPages() {
        String noSuites = "\"checkSuites\": { \"nodes\": [] }";

        assertEquals(
                Either.right(new BranchChecks(
                        "owner/repo",
                        HashMap.of(
                                "feature/login",
                                Checks.PASSING,
                                "wip",
                                Checks.NONE,
                                "main",
                                Checks.FAILING,
                                "fix",
                                Checks.PENDING))),
                branches("["
                        + page(branch("feature/login", "{ \"state\": \"SUCCESS\" }", noSuites) + ", "
                                + branch("wip", "null", noSuites))
                        + ", "
                        + page(branch("main", "{ \"state\": \"FAILURE\" }", noSuites) + ", "
                                + branch("fix", "{ \"state\": \"EXPECTED\" }", noSuites))
                        + "]"));
    }

    @Test
    void aBranchWhoseWorkflowWaitsToStartIsPendingUnlessItsChecksFailed() {
        assertEquals(
                Either.right(new BranchChecks(
                        "owner/repo",
                        HashMap.of(
                                "main",
                                Checks.PENDING,
                                "broken",
                                Checks.FAILING,
                                "feature",
                                Checks.PASSING,
                                "wip",
                                Checks.NONE))),
                branches("["
                        + page(branch("main", "null", WAITING_SUITES) + ", "
                                + branch("broken", "{ \"state\": \"FAILURE\" }", WAITING_SUITES) + ", "
                                + branch("feature", "{ \"state\": \"SUCCESS\" }", RAN_SUITES) + ", "
                                + branch("wip", "null", "\"checkSuites\": { \"nodes\": [] }"))
                        + "]"));
    }

    @Test
    void aBranchWhoseTipIsNoCommitHasNoChecks() {
        assertEquals(
                Either.right(new BranchChecks("owner/repo", HashMap.of("odd", Checks.NONE))),
                branches("[" + page("{ \"name\": \"odd\", \"target\": { \"__typename\": \"Tree\" } }") + "]"));
    }

    @Test
    void noPagesOrABranchWithoutItsTargetIsUnexpected() {
        assertEquals(Either.left(new GhError.UnexpectedOutput("expected a page")), branches("[]"));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("[0].data.repository.refs.nodes[0].target: missing")),
                branches("[" + page("{ \"name\": \"main\" }") + "]"));
    }

    @Test
    void allPagesAreAskedForAsOneArray() {
        assertTrue(BranchesQuery.COMMAND
                .executed()
                .commandLine()
                .containsSlice(io.vavr.collection.List.of("--paginate", "--slurp")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aGhThatDoesNotKnowSlurpIsTooOld(@TempDir Path dir) throws Exception {
        Path gh = Files.writeString(
                dir.resolve("gh"),
                "#!/bin/sh\necho 'unknown flag: --slurp' >&2\necho '' >&2\necho 'Usage:  gh api <endpoint> [flags]' >&2\n"
                        + "exit 1\n");
        assertTrue(gh.toFile().setExecutable(true));

        assertEquals(
                Either.left(new GhError.TooOld("2.48")),
                new CliGitHub(dir, gh.toString(), GhToken.NONE).branchChecks());
    }
}
