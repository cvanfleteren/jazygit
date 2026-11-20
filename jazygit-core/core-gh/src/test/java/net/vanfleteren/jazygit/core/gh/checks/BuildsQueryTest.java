package net.vanfleteren.jazygit.core.gh.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhProcess;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;

/**
 * The builds on the commits of a branch, read some commits at a time (ADR 131).
 */
class BuildsQueryTest {

    private static final String AAA = "a".repeat(40);
    private static final String BBB = "b".repeat(40);

    private static Either<GhError.UnexpectedOutput, Option<BuildsPage>> page(String ref) {
        return GhProcess.decoded("{ \"data\": { \"repository\": { \"ref\": " + ref + " } } }", BuildsQuery.DECODER);
    }

    private static String history(boolean more, String commits) {
        return """
                { "target": { "__typename": "Commit", "history": {
                  "pageInfo": { "hasNextPage": %s, "endCursor": "abc 25" }, "nodes": [ %s ] } } }""".formatted(more, commits);
    }

    private static String commit(String sha, String title, String checks) {
        return commit(sha, title, checks, "");
    }

    private static String commit(String sha, String title, String checks, String suites) {
        return """
                { "oid": "%s", "message": "%s",
                  "statusCheckRollup": { "contexts": { "nodes": [ %s ] } },
                  "checkSuites": { "nodes": [ %s ] } }""".formatted(sha, title, checks, suites);
    }

    private static String suite(String status, int checkRuns, long runId, String event) {
        return """
                { "status": "%s", "checkRuns": { "totalCount": %d }, "workflowRun": { "databaseId": %d,
                  "createdAt": "2026-11-14T23:10:00Z", "runAttempt": 1, "event": "%s",
                  "workflow": { "name": "Publish", "id": "W_1" },
                  "file": { "path": ".github/workflows/publish.yml" } } }""".formatted(status, checkRuns, runId, event);
    }

    private static String job(long id, String name, String conclusion, long runId, int attempt, String event) {
        return """
                { "__typename": "CheckRun", "detailsUrl": null, "databaseId": %d, "name": "%s", "status": "COMPLETED",
                  "conclusion": %s, "startedAt": "2026-11-14T23:00:10Z", "completedAt": "2026-11-14T23:04:00Z",
                  "checkSuite": { "workflowRun": { "databaseId": %d, "createdAt": "2026-11-14T23:00:00Z",
                    "runAttempt": %d, "event": "%s", "workflow": { "name": "Publish", "id": "W_1" },
                    "file": { "path": ".github/workflows/publish.yml" } } } }""".formatted(id, name, conclusion == null ? "null" : "\"" + conclusion + "\"", runId, attempt, event);
    }

    @Test
    void theWorkflowRunsOnEachCommitWithTheirCheckRunsAsJobs() {
        BuildsPage page = page(history(
                        true,
                        commit(
                                        AAA,
                                        "Newest",
                                        job(1, "build", "SUCCESS", 10, 1, "push") + ", "
                                                + job(2, "verify / acceptance (1/6)", "FAILURE", 10, 1, "push")
                                                + ", { \"__typename\": \"StatusContext\" }")
                                + ", " + commit(BBB, "Older", "")
                                + ", { \"oid\": \"" + "c".repeat(40)
                                + "\", \"message\": \"No checks\", \"statusCheckRollup\": null, \"checkSuites\": null }"))
                .get()
                .get();

        Build build = page.builds().single();
        assertEquals("Publish", build.workflow());
        assertEquals("push", build.event());
        assertEquals(10, build.runId());
        assertEquals(new Sha(AAA), build.sha());
        assertEquals("Newest", build.title());
        assertEquals(Instant.parse("2026-11-14T23:00:00Z"), build.createdAt());
        assertEquals(List.of("build", "verify / acceptance (1/6)"), build.jobs().map(Build.Job::name));
        assertEquals(Build.Outcome.FAILED, build.outcome());
        assertEquals(Option.of(".github/workflows/publish.yml"), build.file());
        assertEquals(3, page.commits());
        assertEquals(Option.of("abc 25"), page.next());
    }

    @Test
    void aRunHeldBackByItsConcurrencyGroupIsABuildWithoutAJobYet() {
        BuildsPage page = page(history(
                        false,
                        commit(
                                AAA,
                                "Tip",
                                job(1, "build", "SUCCESS", 10, 1, "push"),
                                suite("QUEUED", 0, 12, "push") + ", "
                                        + suite("COMPLETED", 1, 10, "push") + ", "
                                        + suite("PENDING", 0, 13, "issue_comment") + ", "
                                        + suite("COMPLETED", 0, 14, "push") + ", "
                                        + "{ \"status\": \"QUEUED\", \"checkRuns\": { \"totalCount\": 0 }, "
                                        + "\"workflowRun\": null }")))
                .get()
                .get();

        assertEquals(List.of(12L, 10L), page.builds().map(Build::runId));
        Build held = page.builds().head();
        assertEquals("Publish", held.workflow());
        assertEquals("push", held.event());
        assertEquals(1, held.attempt());
        assertEquals(new Sha(AAA), held.sha());
        assertEquals("Tip", held.title());
        assertEquals(Instant.parse("2026-11-14T23:10:00Z"), held.createdAt());
        assertTrue(held.jobs().isEmpty());
        assertEquals(Build.Outcome.RUNNING, held.outcome());
        assertEquals(Option.of(".github/workflows/publish.yml"), held.file());
        assertEquals(Option.of("W_1"), held.origin().workflowId());
    }

    @Test
    void aRunWhoseFirstJobShowsWhileItsSuiteStillWaitsIsOneBuild() {
        // GitHub can answer while the run gets its first job: its suite still counts none, the commit has one.
        BuildsPage page = page(history(
                        false,
                        commit(AAA, "Tip", job(1, "build", "SUCCESS", 12, 1, "push"), suite("QUEUED", 0, 12, "push"))))
                .get()
                .get();

        Build build = page.builds().single();
        assertEquals(12, build.runId());
        assertEquals(List.of("build"), build.jobs().map(Build.Job::name));
    }

    @Test
    void aWaitingRunWhoseIdCannotBeReadIsNoBuild() {
        BuildsPage page =
                page(history(false, commit(AAA, "Tip", "", """
                                { "status": "QUEUED", "checkRuns": { "totalCount": 0 }, "workflowRun": {
                                  "databaseId": null, "createdAt": "2026-11-14T23:10:00Z", "runAttempt": 1,
                                  "event": "push", "workflow": null, "file": null } }"""))).get().get();

        assertTrue(page.builds().isEmpty());
    }

    @Test
    void theCallAsksForTheLinkOfEachCheckRun() {
        assertTrue(BuildsQuery.QUERY.contains("detailsUrl"), BuildsQuery.QUERY);
    }

    @Test
    void theCallAsksForTheWorkflowFileAndIdOfTheSuites() {
        assertTrue(BuildsQuery.QUERY.contains(
                "workflowRun { databaseId createdAt runAttempt event workflow { name id } file { path } }"));
    }

    @Test
    void aRunStartedByACommentIsNoBuild() {
        BuildsPage page = page(history(false, commit(AAA, "Tip", job(1, "claude", "SKIPPED", 11, 1, "issue_comment"))))
                .get()
                .get();

        assertTrue(page.builds().isEmpty());
    }

    @Test
    void theLastCommitsHaveNoneAfterThem() {
        assertEquals(Option.none(), page(history(false, "")).get().get().next());
    }

    @Test
    void aBranchNotOnGitHubHasNoBuilds() {
        assertEquals(Either.right(Option.none()), page("null"));
    }

    @Test
    void theCallAsksForTheCommitsAfterTheCursor() {
        List<String> args = BuildsQuery.command("main", Option.of("abc 25")).args();

        assertTrue(args.containsSlice(List.of("-f", "branch=refs/heads/main")));
        assertTrue(args.containsSlice(List.of("-f", "after=abc 25")));
        assertTrue(args.containsSlice(List.of("-F", "count=25")));
    }

    @Test
    void theFirstCallStartsAtTheTip() {
        assertTrue(BuildsQuery.command("main", Option.none()).args().forAll(arg -> !arg.startsWith("after=")));
    }

    @Test
    void theTitleIsTheWholeFirstLineOfTheMessage() {
        String title = "ADR 129: the command log takes the focus, and shows when each operation was done (#488)";
        BuildsPage page = page(history(
                        false, commit(AAA, title + "\\n\\nThe body.", job(1, "build", "SUCCESS", 10, 1, "push"))))
                .get()
                .get();

        assertEquals(title, page.builds().single().title());
    }

    @Test
    void aCommitWhoseIdIsNotAnObjectIdIsNotRead() {
        assertTrue(page(history(false, commit("not-an-oid", "Tip", ""))).isLeft());
        assertTrue(page(history(false, commit("", "Tip", ""))).isLeft());
    }
}
