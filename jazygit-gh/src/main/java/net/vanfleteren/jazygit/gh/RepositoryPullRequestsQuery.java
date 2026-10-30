package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.fail;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map4;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The open pull requests of the repository, all pages of them, the most recently updated first, the one opened last
 * in any state, and the account gh runs as (ADR 29, 42). The branches show those of them that are not from a fork,
 * so this one read serves both (ADR 58).
 */
final class RepositoryPullRequestsQuery {

    /*
     * gh fills in the repository of the working tree for {owner} and {repo}, and $endCursor while it reads the pages,
     * each of which has the viewer and the one opened last again.
     */
    static final String QUERY = """
            query($owner: String!, $name: String!, $endCursor: String) { viewer { login } \
            repository(owner: $owner, name: $name) { nameWithOwner \
            latest: pullRequests(first: 1, orderBy: {field: CREATED_AT, direction: DESC}) { nodes { number } } \
            pullRequests(states: OPEN, first: 100, after: $endCursor, orderBy: {field: UPDATED_AT, direction: DESC}) \
            { totalCount pageInfo { hasNextPage endCursor } nodes { ...summary } } } } """ + SummaryFragment.FRAGMENT;

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

    // The number of the pull request opened last, 0 for none.
    static final Decoder<Integer> LATEST = at("latest.nodes", list(field("number", integer())))
            .map(latest -> latest.headOption().getOrElse(0));

    // The account gh runs as; none when GitHub does not say.
    static final Decoder<Option<String>> VIEWER =
            at("viewer.login", nullable(string())).map(login -> login.filter(name -> !name.isBlank()));

    // A page: all of it but its pull requests is the same on every page.
    static final Decoder<RepositoryPullRequests> PAGE = field(
            "data",
            map4(
                    at("repository.nameWithOwner", string()),
                    at(
                            "repository.pullRequests",
                            SummaryFragment.pullRequests(
                                    "nodes", SummaryFragment.SUMMARY.map(Option::some), "totalCount")),
                    VIEWER,
                    field("repository", LATEST),
                    RepositoryPullRequests::new));

    // The pages, as the first one with the pull requests of all of them.
    static final Decoder<RepositoryPullRequests> DECODER = list(PAGE)
            .flatMap(pages -> pages.headOption()
                    .fold(
                            () -> fail("expected a page"),
                            first -> succeed(new RepositoryPullRequests(
                                    first.repository(),
                                    new PullRequestList(
                                            pages.flatMap(page -> page.open().pullRequests()),
                                            first.open().total()),
                                    first.viewer(),
                                    first.latest()))));

    private RepositoryPullRequestsQuery() {}
}
