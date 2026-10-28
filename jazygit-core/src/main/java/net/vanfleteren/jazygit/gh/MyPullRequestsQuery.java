package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.map2;

import net.vanfleteren.jazygit.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The open pull requests that ask for the review of the account gh runs as, and the ones it opened, in any
 * repository, the most recently updated first (ADR 42). They are found by a search of issues, whose hits ask for
 * their {@code __typename}: one that is not a pull request answers nothing else, and is left out.
 */
final class MyPullRequestsQuery {

    static final String LIMIT = "50";
    static final String SEARCH = "is:pr is:open archived:false sort:updated-desc ";

    static final String QUERY = """
            query($reviewRequested: String!, $authored: String!, $limit: Int!) { \
            reviewRequested: search(type: ISSUE, query: $reviewRequested, first: $limit) \
            { issueCount nodes { __typename ...summary } } \
            authored: search(type: ISSUE, query: $authored, first: $limit) { issueCount nodes { __typename ...summary } } } \
            """ + SummaryFragment.FRAGMENT;

    static final GhCommand COMMAND = gh(
            "api",
            "graphql",
            "-f",
            "reviewRequested=" + SEARCH + "review-requested:@me",
            "-f",
            "authored=" + SEARCH + "author:@me",
            "-F",
            "limit=" + LIMIT,
            "-f",
            "query=" + QUERY);

    static final Decoder<MyPullRequests> DECODER = map2(
            at("data.reviewRequested", SummaryFragment.pullRequests("nodes", SummaryFragment.HIT, "issueCount")),
            at("data.authored", SummaryFragment.pullRequests("nodes", SummaryFragment.HIT, "issueCount")),
            MyPullRequests::new);

    private MyPullRequestsQuery() {}
}
