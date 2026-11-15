package net.vanfleteren.jazygit.gh.checks;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Comparator;
import net.vanfleteren.jazygit.gh.graphql.CheckFragment;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.process.GhCommand;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The builds on the commits of a branch, some commits at a time: the workflow runs on each, with their check runs as
 * jobs (ADR 42, 130).
 */
final class BuildsQuery {

    /**
     * The commits read in one call: 100 take 7 to 9 s, close to the 10 s GitHub gives a call (ADR 130).
     */
    static final int COMMITS = 25;

    /**
     * The events whose runs build the code; a run started by a comment or an issue does not (ADR 130).
     */
    static final Set<String> EVENTS =
            HashSet.of("push", "pull_request", "pull_request_target", "merge_group", "workflow_dispatch", "schedule");

    // gh fills in the repository of the working tree for {owner} and {repo}.
    static final String QUERY = """
            query($owner: String!, $name: String!, $branch: String!, $count: Int!, $after: String) { \
            repository(owner: $owner, name: $name) { ref(qualifiedName: $branch) { target { __typename \
            ... on Commit { history(first: $count, after: $after) { pageInfo { hasNextPage endCursor } \
            nodes { oid messageHeadline %s } } } } } } }""".formatted(
                    "statusCheckRollup { contexts(first: 100) { nodes { __typename ... on CheckRun { %s } } } }"
                            .formatted(CheckFragment.CHECK_RUN_FIELDS));

    // The builds on one commit, the newest first.
    static final Decoder<List<Build>> COMMIT = map3(
            field("oid", string()), field("messageHeadline", string()), RecentChecksQuery.COMMIT, BuildsQuery::builds);

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
     * The builds on the commit {@code sha} titled {@code title} that {@code checks} tell, the newest first: its check
     * runs grouped by their workflow run, of an event that builds the code.
     */
    private static List<Build> builds(String sha, String title, List<Check> checks) {
        List<Tuple2<String, CheckRun>> runs = checks.flatMap(
                        check -> check.run().map(run -> Tuple.of(check.name(), run)))
                .filter(named -> EVENTS.contains(named._2().event()));
        return runs.map(named -> named._2().runId())
                .distinct()
                .map(runId -> runs.filter(named -> named._2().runId() == runId))
                .map(ofRun -> {
                    CheckRun first = ofRun.head()._2();
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
                                    named._2().completedAt())));
                })
                .sorted(Comparator.comparing(Build::createdAt).reversed());
    }
}
