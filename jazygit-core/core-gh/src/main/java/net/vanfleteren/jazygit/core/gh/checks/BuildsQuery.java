package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.lib.json.Decoder.at;
import static net.vanfleteren.jazygit.lib.json.Decoder.bool;
import static net.vanfleteren.jazygit.lib.json.Decoder.byField;
import static net.vanfleteren.jazygit.lib.json.Decoder.field;
import static net.vanfleteren.jazygit.lib.json.Decoder.instant;
import static net.vanfleteren.jazygit.lib.json.Decoder.integer;
import static net.vanfleteren.jazygit.lib.json.Decoder.list;
import static net.vanfleteren.jazygit.lib.json.Decoder.longValue;
import static net.vanfleteren.jazygit.lib.json.Decoder.map2;
import static net.vanfleteren.jazygit.lib.json.Decoder.map4;
import static net.vanfleteren.jazygit.lib.json.Decoder.map6;
import static net.vanfleteren.jazygit.lib.json.Decoder.nullable;
import static net.vanfleteren.jazygit.lib.json.Decoder.string;
import static net.vanfleteren.jazygit.lib.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.Comparator;
import net.vanfleteren.jazygit.core.gh.graphql.CheckFragment;
import net.vanfleteren.jazygit.core.gh.graphql.ObjectIds;
import net.vanfleteren.jazygit.core.gh.graphql.SuiteFragment;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.process.GhCommand;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.lib.json.Decoder;

/**
 * The builds on the commits of a branch, some commits at a time: the workflow runs on each, with their check runs as
 * jobs (ADR 42, 131).
 */
final class BuildsQuery {

    /**
     * The commits read in one call: 100 take 7 to 9 s, close to the 10 s GitHub gives a call (ADR 131).
     */
    static final int COMMITS = 25;

    /**
     * The events whose runs build the code; a run started by a comment or an issue does not (ADR 131).
     */
    static final Set<String> EVENTS =
            HashSet.of("push", "pull_request", "pull_request_target", "merge_group", "workflow_dispatch", "schedule");

    // gh fills in the repository of the working tree for {owner} and {repo}.
    static final String QUERY = """
            query($owner: String!, $name: String!, $branch: String!, $count: Int!, $after: String) { \
            repository(owner: $owner, name: $name) { ref(qualifiedName: $branch) { target { __typename \
            ... on Commit { history(first: $count, after: $after) { pageInfo { hasNextPage endCursor } \
            nodes { oid message %s %s } } } } } } }""".formatted(
                    "statusCheckRollup { contexts(first: 100) { nodes { __typename ... on CheckRun { %s } } } }"
                            .formatted(CheckFragment.CHECK_RUN_FIELDS.replace(
                                    "workflow { name }", "workflow { name id } file { path }")),
                    SuiteFragment.FIELDS.replace("workflow { name }", "workflow { name id } file { path }"));

    // A check run, with the file and the id of its workflow (ADR 131); a commit status answers only its __typename, and
    // is left out.
    static final Decoder<Option<Tuple2<Check, Workflow>>> CHECK_RUN = byField(
            "__typename",
            HashMap.of(
                    "CheckRun",
                    map2(
                                    CheckFragment.CHECK,
                                    field(
                                                    "checkSuite",
                                                    nullable(field(
                                                            "workflowRun",
                                                            nullable(map2(
                                                                    field("file", nullable(field("path", string()))),
                                                                    field("workflow", nullable(field("id", string()))),
                                                                    Workflow::new)))))
                                            .map(suite -> suite.flatMap(run -> run)
                                                    .getOrElse(new Workflow(Option.none(), Option.none()))),
                                    Tuple::of)
                            .map(Option::some)),
            succeed(Option.none()));

    // The check runs on a commit.
    static final Decoder<List<Tuple2<Check, Workflow>>> CHECKS = field(
                    "statusCheckRollup", nullable(at("contexts.nodes", list(CHECK_RUN))))
            .map(checks -> checks.toList().flatMap(nodes -> nodes).flatMap(check -> check));

    // A workflow run that waits to start, as one held back by its concurrency group, which has no check run until it
    // starts (ADR 35): a build without a job yet. None when its id or its start cannot be read.
    static final Decoder<Option<Held>> HELD = map6(
            field("databaseId", nullable(longValue())),
            field("createdAt", nullable(instant())),
            field("runAttempt", nullable(integer())),
            field("event", nullable(string())),
            field(
                    "workflow",
                    nullable(map2(field("name", nullable(string())), field("id", nullable(string())), Tuple::of))),
            field("file", nullable(field("path", string()))),
            (id, createdAt, attempt, event, workflow, file) -> id.flatMap(runId -> createdAt.map(created -> new Held(
                    workflow.flatMap(Tuple2::_1).getOrElse(""),
                    event.getOrElse(""),
                    runId,
                    attempt.getOrElse(1),
                    created,
                    new Workflow(file, workflow.flatMap(Tuple2::_2))))));

    // The builds on one commit, the newest first. Its title is the first line of its message, as GitHub's
    // messageHeadline cuts it off at 72 characters with an ellipsis.
    static final Decoder<List<Build>> COMMIT = map4(
            field("oid", ObjectIds.SHA),
            field("message", string())
                    .map(message -> message.lines().findFirst().orElse("")),
            CHECKS,
            SuiteFragment.waiting(HELD),
            BuildsQuery::builds);

    // The page of commits of a branch; none in a repository without a commit.
    static final Decoder<Option<BuildsPage>> HISTORY = byField(
            "__typename",
            HashMap.of(
                    "Commit",
                    field(
                                    "history",
                                    map2(
                                            field(
                                                    "pageInfo",
                                                    map2(
                                                            field("hasNextPage", bool()),
                                                            field("endCursor", nullable(string())),
                                                            (more, cursor) -> cursor.filter(at -> more))),
                                            field("nodes", list(COMMIT)),
                                            (next, commits) -> new BuildsPage(
                                                    commits.flatMap(onCommit -> onCommit), commits.size(), next)))
                            .map(Option::some)),
            succeed(Option.none()));

    /**
     * The builds read; none when the branch is not on GitHub, or has no commit.
     */
    static final Decoder<Option<BuildsPage>> DECODER =
            at("data.repository.ref", nullable(field("target", HISTORY)).map(page -> page.flatMap(read -> read)));

    private BuildsQuery() {}

    /**
     * The call reading the builds on {@link #COMMITS} commits of {@code branch}, the newest first, from {@code after}
     * on, or from its tip without.
     */
    static GhCommand command(String branch, Option<String> after) {
        List<String> args = List.of(
                        "api",
                        "graphql",
                        "-F",
                        "owner={owner}",
                        "-F",
                        "name={repo}",
                        "-f",
                        "branch=refs/heads/" + branch,
                        "-F",
                        "count=" + COMMITS)
                .appendAll(after.toList().flatMap(cursor -> List.of("-f", "after=" + cursor)))
                .appendAll(List.of("-f", "query=" + QUERY));
        return gh(args.toJavaArray(String[]::new));
    }

    /**
     * The builds on the commit {@code sha} titled {@code title} that {@code checks} and {@code held} tell, the newest
     * first: its check runs grouped by their workflow run, and its workflow runs waiting to start without a job, of an
     * event that builds the code.
     */
    private static List<Build> builds(Sha sha, String title, List<Tuple2<Check, Workflow>> checks, List<Held> held) {
        List<Tuple3<String, CheckRun, Workflow>> runs = checks.flatMap(
                        check -> check._1().run().map(run -> Tuple.of(check._1().name(), run, check._2())))
                .filter(named -> EVENTS.contains(named._2().event()));
        return runs.map(named -> named._2().runId())
                .distinct()
                .map(runId -> runs.filter(named -> named._2().runId() == runId))
                .map(ofRun -> {
                    CheckRun first = ofRun.head()._2();
                    Workflow workflow = ofRun.map(Tuple3::_3)
                            .find(known -> known.path().isDefined())
                            .getOrElse(ofRun.head()._3());
                    return new Build(
                            first.workflow(),
                            first.event(),
                            first.runId(),
                            ofRun.map(named -> named._2().runAttempt()).max().getOrElse(1),
                            sha,
                            title,
                            first.runCreatedAt(),
                            ofRun.map(named -> new Build.Job(
                                    named._1(),
                                    named._2().conclusion(),
                                    named._2().startedAt(),
                                    named._2().completedAt())),
                            workflow.path(),
                            new Build.Origin(workflow.id(), Option.none(), Option.none()));
                })
                .appendAll(held.filter(waiting -> EVENTS.contains(waiting.event()))
                        .filter(waiting -> runs.forAll(named -> named._2().runId() != waiting.runId()))
                        .map(waiting -> waiting.build(sha, title)))
                .sorted(Comparator.comparing(Build::createdAt).reversed());
    }

    /**
     * The workflow of a check run: the path of its file and its id, as far as GitHub says.
     */
    record Workflow(Option<String> path, Option<String> id) {}

    /**
     * A workflow run that waits to start, without a job yet.
     */
    record Held(String workflow, String event, long runId, int attempt, Instant createdAt, Workflow source) {

        Build build(Sha sha, String title) {
            return new Build(
                    workflow,
                    event,
                    runId,
                    attempt,
                    sha,
                    title,
                    createdAt,
                    List.empty(),
                    source.path(),
                    new Build.Origin(source.id(), Option.none(), Option.none()));
        }
    }
}
