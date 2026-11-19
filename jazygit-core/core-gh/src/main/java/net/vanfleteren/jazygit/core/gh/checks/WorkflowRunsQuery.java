package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.lib.json.Decoder.at;
import static net.vanfleteren.jazygit.lib.json.Decoder.bool;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.instant;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.longValue;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.map5;
import static net.vanfleteren.jazygit.lib.json.Decoder.map6;
import static net.vanfleteren.jazygit.lib.json.Decoder.nullable;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * The runs of one workflow on every branch, some at a time, the newest first, each with its jobs: how long a workflow
 * takes in general, not only on one branch (ADR 42, 131).
 */
final class WorkflowRunsQuery {

    /**
     * The runs read in one call: 50 with their jobs take under 2 s.
     */
    static final int RUNS = 25;

    static final String QUERY = """
            query($id: ID!, $count: Int!, $after: String) { node(id: $id) { ... on Workflow { name \
            runs(first: $count, after: $after) { pageInfo { hasNextPage endCursor } nodes { databaseId createdAt \
            runAttempt event file { path } checkSuite { status branch { name } commit { oid message } \
            matchingPullRequests(first: 1) { nodes { number } } \
            checkRuns(first: 50) { nodes { name status conclusion startedAt completedAt } } } } } } } }""";

    // A job of a run: running until it is completed, then how it ended.
    static final Decoder<Build.Job> JOB = map5(
            field("name", string()),
            field("status", string()),
            field("conclusion", nullable(string())),
            field("startedAt", nullable(instant())),
            field("completedAt", nullable(instant())),
            (name, status, conclusion, started, completed) -> new Build.Job(
                    name, status.equals("COMPLETED") ? conclusion.getOrElse("") : "", started, completed));

    // Whether a run is done, its commit, its branch, its pull request and its jobs.
    static final Decoder<Suite> SUITE = map5(
            field("status", string()).map(status -> status.equals("COMPLETED")),
            field("branch", nullable(field("name", string()))),
            field(
                    "commit",
                    map2(
                            field("oid", string()),
                            field("message", string()),
                            (oid, message) ->
                                    new Commit(oid, message.lines().findFirst().orElse("")))),
            at("matchingPullRequests.nodes", list(field("number", integer()))).map(List::headOption),
            at("checkRuns.nodes", list(JOB)),
            Suite::new);

    private WorkflowRunsQuery() {}

    /**
     * The runs read, as builds of the workflow {@code workflow}, whose id is {@code id}; none when it is not a workflow.
     * A run waiting to start has no job yet; one done without a job, as one that could not start, is no build, as on a
     * branch (ADR 131).
     */
    static Decoder<Option<WorkflowRunsPage>> decoder(String id) {
        return at(
                "data.node",
                nullable(map2(
                        field("name", string()),
                        field(
                                "runs",
                                map2(
                                        field(
                                                "pageInfo",
                                                map2(
                                                        field("hasNextPage", bool()),
                                                        field("endCursor", nullable(string())),
                                                        (more, cursor) -> cursor.filter(at -> more))),
                                        field("nodes", list(run())),
                                        RunsRead::new)),
                        (name, read) -> new WorkflowRunsPage(
                                read.runs()
                                        .filter(run -> !run.suite().done()
                                                || !run.suite().jobs().isEmpty())
                                        .map(run -> run.build(name, id))
                                        .filter(build -> BuildsQuery.EVENTS.contains(build.event())),
                                read.runs().size(),
                                read.next()))));
    }

    /**
     * The call reading {@link #RUNS} runs of the workflow {@code id}, the newest first, from {@code after} on, or from
     * the newest without.
     */
    static GhCommand command(String id, Option<String> after) {
        List<String> args = List.of("api", "graphql", "-f", "id=" + id, "-F", "count=" + RUNS)
                .appendAll(after.toList().flatMap(cursor -> List.of("-f", "after=" + cursor)))
                .appendAll(List.of("-f", "query=" + QUERY));
        return gh(args.toJavaArray(String[]::new));
    }

    private static Decoder<Run> run() {
        return map6(
                field("databaseId", longValue()),
                field("createdAt", instant()),
                field("runAttempt", nullable(integer())),
                field("event", string()),
                field("file", nullable(field("path", string()))),
                field("checkSuite", SUITE),
                Run::new);
    }

    private record Commit(String sha, String title) {}

    private record Suite(
            boolean done, Option<String> branch, Commit commit, Option<Integer> pullRequest, List<Build.Job> jobs) {}

    private record RunsRead(Option<String> next, List<Run> runs) {}

    private record Run(
            long id, Instant createdAt, Option<Integer> attempt, String event, Option<String> file, Suite suite) {

        Build build(String workflow, String workflowId) {
            return new Build(
                    workflow,
                    event,
                    id,
                    attempt.getOrElse(1),
                    suite.commit().sha(),
                    suite.commit().title(),
                    createdAt,
                    suite.jobs(),
                    file,
                    new Build.Origin(Option.of(workflowId), suite.branch(), suite.pullRequest()));
        }
    }
}
