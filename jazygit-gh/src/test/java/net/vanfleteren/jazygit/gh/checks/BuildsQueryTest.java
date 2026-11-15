package net.vanfleteren.jazygit.gh.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhProcess;
import org.junit.jupiter.api.Test;

/**
 * The builds on the commits of a branch, read some commits at a time (ADR 131).
 */
class BuildsQueryTest {

    private static Either<GhError.UnexpectedOutput, Option<BuildsPage>> page(String ref) {
        return GhProcess.decoded("{ \"data\": { \"repository\": { \"ref\": " + ref + " } } }", BuildsQuery.DECODER);
    }

    private static String history(boolean more, String commits) {
        return """
                { "target": { "__typename": "Commit", "history": {
                  "pageInfo": { "hasNextPage": %s, "endCursor": "abc 25" }, "nodes": [ %s ] } } }""".formatted(more, commits);
    }

    private static String commit(String sha, String title, String checks) {
        return """
                { "oid": "%s", "message": "%s",
                  "statusCheckRollup": { "contexts": { "nodes": [ %s ] } } }""".formatted(sha, title, checks);
    }

    private static String job(long id, String name, String conclusion, long runId, int attempt, String event) {
        return """
                { "__typename": "CheckRun", "databaseId": %d, "name": "%s", "status": "COMPLETED",
                  "conclusion": %s, "startedAt": "2026-11-14T23:00:10Z", "completedAt": "2026-11-14T23:04:00Z",
                  "checkSuite": { "workflowRun": { "databaseId": %d, "createdAt": "2026-11-14T23:00:00Z",
                    "runAttempt": %d, "event": "%s", "workflow": { "name": "Publish" },
                    "file": { "path": ".github/workflows/publish.yml" } } } }""".formatted(id, name, conclusion == null ? "null" : "\"" + conclusion + "\"", runId, attempt, event);
    }

    @Test
    void theWorkflowRunsOnEachCommitWithTheirCheckRunsAsJobs() {
        BuildsPage page = page(history(
                        true,
                        commit(
                                        "aaa",
                                        "Newest",
                                        job(1, "build", "SUCCESS", 10, 1, "push") + ", "
                                                + job(2, "verify / acceptance (1/6)", "FAILURE", 10, 1, "push")
                                                + ", { \"__typename\": \"StatusContext\" }")
                                + ", " + commit("bbb", "Older", "")
                                + ", { \"oid\": \"ccc\", \"message\": \"No checks\", \"statusCheckRollup\": null }"))
                .get()
                .get();

        Build build = page.builds().single();
        assertEquals("Publish", build.workflow());
        assertEquals("push", build.event());
        assertEquals(10, build.runId());
        assertEquals("aaa", build.sha());
        assertEquals("Newest", build.title());
        assertEquals(Instant.parse("2026-11-14T23:00:00Z"), build.createdAt());
        assertEquals(List.of("build", "verify / acceptance (1/6)"), build.jobs().map(Build.Job::name));
        assertEquals(Build.Outcome.FAILED, build.outcome());
        assertEquals(Option.of(".github/workflows/publish.yml"), build.file());
        assertEquals(3, page.commits());
        assertEquals(Option.of("abc 25"), page.next());
    }

    @Test
    void aRunStartedByACommentIsNoBuild() {
        BuildsPage page = page(history(
                        false, commit("aaa", "Tip", job(1, "claude", "SKIPPED", 11, 1, "issue_comment"))))
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
                        false, commit("aaa", title + "\\n\\nThe body.", job(1, "build", "SUCCESS", 10, 1, "push"))))
                .get()
                .get();

        assertEquals(title, page.builds().single().title());
    }
}
