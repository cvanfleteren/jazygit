package net.vanfleteren.jazygit.core.gh.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.gh.process.GhProcess;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import org.junit.jupiter.api.Test;

/**
 * The runs of a workflow on every branch, and the workflows of the repository (ADR 131).
 */
class WorkflowRunsQueryTest {

    private static final String RUN = """
            { "databaseId": 7, "createdAt": "2026-11-15T08:00:00Z", "runAttempt": 2, "event": "%s",
              "file": { "path": ".github/workflows/build.yml" },
              "checkSuite": { "status": "IN_PROGRESS", "branch": %s, "commit": { "oid": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "message": "Title\\n\\nBody" },
                "matchingPullRequests": { "nodes": [ { "number": 405 } ] },
                "checkRuns": { "nodes": [
                  { "name": "verify", "status": "COMPLETED", "conclusion": "SUCCESS",
                    "startedAt": "2026-11-15T08:00:05Z", "completedAt": "2026-11-15T08:02:00Z" },
                  { "name": "acceptance", "status": "IN_PROGRESS", "conclusion": null,
                    "startedAt": "2026-11-15T08:02:01Z", "completedAt": null } ] } } }""";

    private static WorkflowRunsPage page(String runs) {
        return GhProcess.decoded("""
                        { "data": { "node": { "name": "Build", "runs": {
                          "pageInfo": { "hasNextPage": true, "endCursor": "c25" }, "nodes": [ %s ] } } } }""".formatted(runs), WorkflowRunsQuery.decoder("W_1"))
                .get()
                .get();
    }

    @Test
    void aRunIsABuildOfTheWorkflowWithItsBranchPullRequestAndJobs() {
        Build build = page(RUN.formatted("pull_request", "{ \"name\": \"feature\" }"))
                .builds()
                .single();

        assertEquals("Build", build.workflow());
        assertEquals(7, build.runId());
        assertEquals(2, build.attempt());
        assertEquals("Title", build.title());
        assertEquals(Option.of(".github/workflows/build.yml"), build.file());
        assertEquals(
                new Build.Origin(Option.of("W_1"), Option.of(new BranchName("feature")), Option.of(new PrNumber(405))),
                build.origin());
        assertEquals(List.of("SUCCESS", ""), build.jobs().map(Build.Job::conclusion));
        assertEquals(Build.Outcome.RUNNING, build.outcome());
    }

    @Test
    void aRunOfADeletedBranchHasNoBranchAndOneStartedByACommentIsNoBuild() {
        WorkflowRunsPage read =
                page(RUN.formatted("pull_request", "null") + ", " + RUN.formatted("issue_comment", "null"));

        assertEquals(Option.none(), read.builds().single().origin().branch());
        assertEquals(2, read.runs());
        assertEquals(Option.of("c25"), read.next());
    }

    private static String jobless(long id, String status) {
        return """
                { "databaseId": %d, "createdAt": "2026-11-15T08:00:00Z", "runAttempt": 1, "event": "push",
                  "file": { "path": ".github/workflows/build.yml" },
                  "checkSuite": { "status": "%s", "branch": { "name": "main" },
                    "commit": { "oid": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "message": "Title" }, "matchingPullRequests": { "nodes": [] },
                    "checkRuns": { "nodes": [] } } }""".formatted(id, status);
    }

    @Test
    void aRunWaitingToStartRunsWithoutAJobAndOneDoneWithoutAJobIsNoBuild() {
        WorkflowRunsPage read = page(jobless(8, "QUEUED") + ", " + jobless(9, "COMPLETED"));

        Build held = read.builds().single();
        assertEquals(8, held.runId());
        assertEquals(Build.Outcome.RUNNING, held.outcome());
        assertEquals(2, read.runs());
    }

    @Test
    void anIdThatIsNoWorkflowHasNoRuns() {
        assertEquals(
                Either.right(Option.none()),
                GhProcess.decoded("{ \"data\": { \"node\": null } }", WorkflowRunsQuery.decoder("W_1")));
    }

    @Test
    void theCallAsksForTheRunsAfterTheCursor() {
        List<String> args = WorkflowRunsQuery.command("W_1", Option.of("c25")).args();

        assertTrue(args.containsSlice(List.of("-f", "id=W_1")));
        assertTrue(args.containsSlice(List.of("-f", "after=c25")));
    }

    @Test
    void theWorkflowsOfTheRepositoryAreReadWithWhetherTheyRun() {
        assertEquals(
                Either.right(List.of(
                        new RepoWorkflow("W_1", "Build", ".github/workflows/build.yml", true),
                        new RepoWorkflow("W_2", "Old", ".github/workflows/old.yml", false))),
                GhProcess.decoded("""
                        { "total_count": 2, "workflows": [
                          { "node_id": "W_1", "name": "Build", "path": ".github/workflows/build.yml", "state": "active" },
                          { "node_id": "W_2", "name": "Old", "path": ".github/workflows/old.yml",
                            "state": "disabled_manually" } ] }""", RepoWorkflowsQuery.DECODER));
    }

    @Test
    void aRunOfACommitWhoseIdIsNotAnObjectIdIsNotRead() {
        String run = RUN.formatted("push", "null")
                .replace("\"oid\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"", "\"oid\": \"not-an-oid\"");

        assertTrue(GhProcess.decoded("""
                        { "data": { "node": { "name": "Build", "runs": {
                          "pageInfo": { "hasNextPage": false, "endCursor": null }, "nodes": [ %s ] } } } }""".formatted(run), WorkflowRunsQuery.decoder("W_1"))
                .isLeft());
    }
}
