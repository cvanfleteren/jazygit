package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map6;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The open pull requests of the repository that are not from a fork, all pages of them, for the branches they are of
 * (ADR 24, 41).
 */
final class PullRequestsQuery {

    /*
     * With the states of the checks on their last commit, and its check suites. gh fills in the repository of the
     * working tree for {owner} and {repo}, and $endCursor while it reads the pages.
     */
    static final String QUERY = """
            query($owner: String!, $name: String!, $endCursor: String) { repository(owner: $owner, name: $name) { \
            pullRequests(states: OPEN, first: 100, after: $endCursor) { pageInfo { hasNextPage endCursor } \
            nodes { number headRefName isDraft isCrossRepository reviewDecision %s \
            commits(last: 1) { nodes { commit { statusCheckRollup { contexts(first: 100) { nodes { %s } } } %s } } } \
            } } } }""".formatted(StackFragment.FIELDS, CheckFragment.STATE_FIELDS, SuiteFragment.FIELDS);

    // All pages, as one array.
    static final GhCommand COMMAND = gh(
            "api",
            "graphql",
            "--paginate",
            "--slurp",
            "-F",
            "owner={owner}",
            "-F",
            "name={repo}",
            "-f",
            "query=" + QUERY);

    // The checks on a commit: the state of each, and PENDING for each workflow run that waits to start.
    static final Decoder<Checks> COMMIT = map2(
            field("statusCheckRollup", nullable(at("contexts.nodes", list(CheckFragment.STATE)))),
            SuiteFragment.WAITING,
            (states, waiting) -> CheckFragment.checks(states.getOrElse(List.empty())
                    .filter(state -> !state.isEmpty())
                    .appendAll(waiting.map(run -> "PENDING"))));

    // A pull request; none for one from a fork, whose branch is not one of the repository.
    static final Decoder<Option<PullRequest>> PULL_REQUEST = map2(
            field("isCrossRepository", bool()),
            map6(
                    field("number", integer()),
                    field("headRefName", string()),
                    field("isDraft", bool()),
                    field("reviewDecision", nullable(string())),
                    at("commits.nodes", list(field("commit", COMMIT))),
                    StackFragment.DECODER,
                    (number, head, draft, decision, commits, stack) -> new PullRequest(
                            number,
                            head,
                            draft,
                            commits.headOption().getOrElse(Checks.NONE),
                            CliGitHub.review(decision.getOrElse("")),
                            stack)),
            (fromFork, pullRequest) -> Option.when(!fromFork, pullRequest));

    static final Decoder<List<PullRequest>> DECODER = list(at("data.repository.pullRequests.nodes", list(PULL_REQUEST)))
            .map(pages -> pages.flatMap(page -> page).flatMap(pullRequest -> pullRequest));

    private PullRequestsQuery() {}
}
