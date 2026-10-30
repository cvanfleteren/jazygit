package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.RecentChecks;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The check runs on the last commit of the pull requests merged last, and on the last commits of the default branch,
 * to learn how long checks take from (ADR 32, 42).
 */
final class RecentChecksQuery {

    // gh fills in the repository of the working tree for {owner} and {repo}.
    static final String QUERY = """
            query($owner: String!, $name: String!, $count: Int!) { repository(owner: $owner, name: $name) { \
            nameWithOwner \
            pullRequests(first: $count, states: MERGED, orderBy: { field: UPDATED_AT, direction: DESC }) { nodes { \
            commits(last: 1) { nodes { commit { %1$s } } } } } \
            defaultBranchRef { target { __typename ... on Commit { history(first: $count) { nodes { %1$s } } } } } } }""".formatted(
                    "statusCheckRollup { contexts(first: 100) { nodes { __typename ... on CheckRun { %s } } } }"
                            .formatted(CheckFragment.CHECK_RUN_FIELDS));

    static final GhCommand COMMAND = gh(
            "api",
            "graphql",
            "-F",
            "owner={owner}",
            "-F",
            "name={repo}",
            "-F",
            "count=" + CheckTimings.TOP_UP_COMMITS,
            "-f",
            "query=" + QUERY);

    // A check run; a commit status answers only its __typename, and is left out.
    static final Decoder<Option<Check>> CHECK_RUN = byField(
            "__typename", HashMap.of("CheckRun", CheckFragment.CHECK.map(Option::some)), succeed(Option.<Check>none()));

    // The check runs on a commit.
    static final Decoder<List<Check>> COMMIT = field(
                    "statusCheckRollup", nullable(at("contexts.nodes", list(CHECK_RUN))))
            .map(checks -> checks.toList().flatMap(nodes -> nodes).flatMap(check -> check));

    // The check runs of the pull requests merged last, on their last commit.
    static final Decoder<List<Check>> MERGED = at(
                    "pullRequests.nodes", list(at("commits.nodes", list(field("commit", COMMIT)))))
            .map(pullRequests -> pullRequests.flatMap(commits -> commits).flatMap(checks -> checks));

    // The check runs of the last commits of the default branch; none in a repository without one.
    static final Decoder<List<Check>> DEFAULT_BRANCH = field(
                    "defaultBranchRef",
                    nullable(field(
                            "target",
                            byField(
                                    "__typename",
                                    HashMap.of("Commit", at("history.nodes", list(COMMIT))),
                                    succeed(List.empty())))))
            .map(commits -> commits.toList().flatMap(commit -> commit).flatMap(checks -> checks));

    /**
     * The repository, and the check runs of a workflow, which tell how long it took: the others are left out.
     */
    static final Decoder<RecentChecks> DECODER = at(
            "data.repository",
            map3(
                    field("nameWithOwner", string()),
                    MERGED,
                    DEFAULT_BRANCH,
                    (repository, merged, defaultBranch) -> new RecentChecks(
                            repository,
                            merged.appendAll(defaultBranch)
                                    .filter(check -> check.run().isDefined()))));

    private RecentChecksQuery() {}
}
