package net.vanfleteren.jazygit.gh;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * Parts of what GitHub answers that more than one query reads, as JSON, and what is decoded of them (ADR 42).
 */
final class GitHubAnswers {

    // The suites of the commit on main while its publish workflow waited for the run before it: the claude app's
    // suite, which stays queued for good, the waiting workflow, and a workflow whose jobs were all skipped.
    static final String WAITING_SUITES = """
            "checkSuites": { "nodes": [
              { "status": "QUEUED", "checkRuns": { "totalCount": 0 }, "workflowRun": null },
              { "status": "PENDING", "checkRuns": { "totalCount": 0 }, "workflowRun": {
                "databaseId": 404, "createdAt": "2026-10-26T23:32:02Z", "runAttempt": 1, "event": "push",
                "workflow": { "name": "Publish" } } },
              { "status": "COMPLETED", "checkRuns": { "totalCount": 0 }, "workflowRun": {
                "databaseId": 403, "createdAt": "2026-10-26T23:00:00Z", "runAttempt": 1, "event": "push",
                "workflow": { "name": "Skipped" } } } ] }""";
    // The suites of a commit whose workflow ran.
    static final String RAN_SUITES = """
            "checkSuites": { "nodes": [
              { "status": "QUEUED", "checkRuns": { "totalCount": 0 }, "workflowRun": null },
              { "status": "COMPLETED", "checkRuns": { "totalCount": 1 }, "workflowRun": {
                "databaseId": 401, "createdAt": "2026-10-26T22:00:00Z", "runAttempt": 1, "event": "push",
                "workflow": { "name": "Build" } } } ] }""";
    // A check run of the Build workflow that passed.
    static final String VERIFY_PASSED = """
            { "__typename": "CheckRun", "databaseId": 122, "name": "verify", "status": "COMPLETED",
              "conclusion": "SUCCESS", "startedAt": "2026-10-26T22:00:02Z", "completedAt": "2026-10-26T22:02:00Z",
              "checkSuite": { "workflowRun": { "databaseId": 401, "createdAt": "2026-10-26T22:00:00Z",
                "runAttempt": 1, "event": "push", "workflow": { "name": "Build" } } } }""";
    // VERIFY_PASSED as it is decoded.
    static final Check VERIFY = new Check(
            "verify",
            Checks.PASSING,
            Option.of(new CheckRun(
                    122,
                    "Build",
                    "SUCCESS",
                    Option.of(Instant.parse("2026-10-26T22:00:02Z")),
                    Option.of(Instant.parse("2026-10-26T22:02:00Z")),
                    401,
                    Instant.parse("2026-10-26T22:00:00Z"),
                    1,
                    "push")));
    // The publish workflow of WAITING_SUITES that waits to start, as the queued check it is decoded as.
    static final Check WAITING = new Check(
            "Publish",
            Checks.PENDING,
            Option.of(new CheckRun(
                    0,
                    "Publish",
                    "",
                    Option.none(),
                    Option.none(),
                    404,
                    Instant.parse("2026-10-26T23:32:02Z"),
                    1,
                    "push")));

    private GitHubAnswers() {}
}
