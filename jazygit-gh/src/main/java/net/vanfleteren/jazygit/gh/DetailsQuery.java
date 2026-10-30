package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.enumOf;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map4;
import static net.vanfleteren.jazygit.json.Decoder.map5;
import static net.vanfleteren.jazygit.json.Decoder.map8;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.Tuple8;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Activity;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Branches;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Mergeable;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewThreads;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Size;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * Everything about one pull request the dialog shows, its latest commit for its checks (ADR 42).
 */
final class DetailsQuery {

    /*
     * gh fills in the repository of the working tree for {owner} and {repo}. A requested reviewer and a force push
     * are of unions, and ask for their __typename.
     */
    static final String QUERY = """
            query($owner: String!, $name: String!, $number: Int!) { repository(owner: $owner, name: $name) { \
            nameWithOwner url pullRequest(number: $number) { number title author { login } state isDraft headRefName \
            baseRefName headRefOid baseRefOid %s createdAt additions deletions changedFiles mergeable reviewDecision \
            comments { totalCount } \
            labels(first: 100) { nodes { name } } latestReviews(first: 100) { nodes { author { login } state } } \
            reviewRequests(first: 100) { nodes { requestedReviewer { __typename ... on User { login } \
            ... on Bot { login } ... on Mannequin { login } ... on Team { name } } } } \
            timelineItems(last: 1, itemTypes: [HEAD_REF_FORCE_PUSHED_EVENT]) { nodes { __typename \
            ... on HeadRefForcePushedEvent { createdAt } } } \
            commits(last: 1) { totalCount nodes { commit { committedDate \
            statusCheckRollup { contexts(first: 100) { nodes { %s } } } %s } } } \
            reviewThreads(last: 100) { totalCount nodes { isResolved comments { totalCount } } } } } }""".formatted(StackFragment.FIELDS, CheckFragment.FIELDS, SuiteFragment.FIELDS);

    static final Decoder<Status> STATUS = map3(
            field("state", enumOf(State.class)),
            field("isDraft", bool()),
            field("mergeable", nullable(string())).map(state -> mergeable(state.getOrElse(""))),
            Status::new);

    // Not yet known to be behind its base, which another query asks.
    static final Decoder<Branches> BRANCHES = map4(
            field("headRefName", string()),
            field("baseRefName", string()),
            field("headRefOid", nullable(string())).map(oid -> oid.flatMap(GhCommit::parse)),
            field("baseRefOid", nullable(string())).map(oid -> oid.flatMap(GhCommit::parse)),
            (head, base, commit, baseCommit) -> new Branches(head, base, commit, Option.none(), baseCommit));

    // When it was last force pushed; none for a node of another type.
    static final Decoder<Option<Instant>> FORCE_PUSH = byField(
            "__typename",
            HashMap.of("HeadRefForcePushedEvent", field("createdAt", instant()).map(Option::some)),
            succeed(Option.<Instant>none()));

    static final Decoder<Activity> ACTIVITY = map3(
            field("createdAt", instant()),
            at("commits.nodes", list(at("commit.committedDate", instant()))).map(List::headOption),
            at("timelineItems.nodes", list(FORCE_PUSH))
                    .map(pushes -> pushes.flatMap(push -> push).headOption()),
            (createdAt, committed, forcePushed) ->
                    new Activity(createdAt, pushedAt(createdAt, committed, forcePushed)));

    static final Decoder<Size> SIZE = map4(
            field("additions", integer()),
            field("deletions", integer()),
            field("changedFiles", integer()),
            at("commits.totalCount", integer()),
            Size::new);

    // A latest review, by its author, empty for a deleted account; none for one that was dismissed or is pending.
    static final Decoder<Option<Reviewer>> REVIEW = map2(
            field("author", nullable(field("login", string()))).map(login -> login.getOrElse("")),
            field("state", string()).map(DetailsQuery::reviewerState),
            (login, state) -> state.map(reviewed -> new Reviewer(login, reviewed)));

    // Who is asked to review: a user, a bot or a mannequin by their login, a team by its name, else empty.
    static final Decoder<String> REQUESTED = field(
                    "requestedReviewer",
                    nullable(byField(
                            "__typename",
                            HashMap.of(
                                    "User", field("login", string()),
                                    "Bot", field("login", string()),
                                    "Mannequin", field("login", string()),
                                    "Team", field("name", string())),
                            succeed(""))))
            .map(requested -> requested.getOrElse(""));

    /*
     * Who reviewed it, and who is asked to: one asked to review again after an earlier review is asked to, not one
     * who reviewed.
     */
    static final Decoder<List<Reviewer>> REVIEWERS = map2(
            at("latestReviews.nodes", list(REVIEW)).map(reviews -> reviews.flatMap(review -> review)),
            at("reviewRequests.nodes", list(REQUESTED)),
            (reviewed, requested) -> reviewed.filter(reviewer -> !requested.contains(reviewer.login()))
                    .appendAll(requested.map(login -> new Reviewer(login, ReviewerState.REQUESTED))));

    // How many review threads it has, how many of them were read, and of those how many are unresolved and the
    // comments they hold.
    static final Decoder<ReviewThreads> THREADS = map2(
            field("totalCount", integer()),
            field("nodes", list(map2(field("isResolved", bool()), at("comments.totalCount", integer()), Tuple::of))),
            (total, threads) -> new ReviewThreads(
                    total,
                    threads.size(),
                    threads.count(thread -> !thread._1()),
                    threads.map(thread -> thread._2()).sum().intValue()));

    static final Decoder<Conversation> CONVERSATION = map5(
            field("reviewDecision", nullable(string())).map(decision -> CliGitHub.review(decision.getOrElse(""))),
            REVIEWERS,
            at("comments.totalCount", integer()),
            field("reviewThreads", THREADS),
            at("labels.nodes", list(field("name", string()))),
            Conversation::new);

    // The checks on its latest commit, and the workflow runs on it that wait to start; none without a commit.
    static final Decoder<List<Check>> CHECKS = at(
                    "commits.nodes",
                    list(field(
                            "commit",
                            map2(
                                    field(
                                            "statusCheckRollup",
                                            nullable(at("contexts.nodes", list(CheckFragment.CHECK)))),
                                    SuiteFragment.WAITING,
                                    (checks, waiting) ->
                                            checks.getOrElse(List.empty()).appendAll(waiting)))))
            .map(commits -> commits.headOption().getOrElse(List.empty()));

    // What it is and how it stands, its checks and its stack.
    static final Decoder<
                    Tuple3<
                            Tuple8<Integer, String, String, Status, Branches, Activity, Size, Conversation>,
                            List<Check>,
                            Option<PullRequestStack>>>
            PULL_REQUEST = map3(
                    map8(
                            field("number", integer()),
                            field("title", string()),
                            field("author", nullable(field("login", string()))).map(login -> login.getOrElse("")),
                            STATUS,
                            BRANCHES,
                            ACTIVITY,
                            SIZE,
                            CONVERSATION,
                            Tuple::of),
                    CHECKS,
                    StackFragment.DECODER,
                    Tuple::of);

    static final Decoder<PullRequestDetails> DECODER = at(
            "data.repository",
            map3(
                    field("nameWithOwner", string()),
                    field("pullRequest", PULL_REQUEST),
                    field("url", string()),
                    (repository, read, url) -> new PullRequestDetails(
                            read._1()._1(),
                            read._1()._2(),
                            read._1()._3(),
                            read._1()._4(),
                            read._1()._5(),
                            read._1()._6(),
                            read._1()._7(),
                            read._1()._8(),
                            read._2(),
                            read._3(),
                            repository,
                            url)));

    private DetailsQuery() {}

    static GhCommand command(int number) {
        return gh(
                "api",
                "graphql",
                "-F",
                "owner={owner}",
                "-F",
                "name={repo}",
                "-F",
                "number=" + number,
                "-f",
                "query=" + QUERY);
    }

    /**
     * When the branch of a pull request was last pushed to: GitHub no longer says when a commit was pushed, so the
     * latest of when it was opened, its latest commit and its latest force push.
     */
    static Instant pushedAt(Instant createdAt, Option<Instant> committed, Option<Instant> forcePushed) {
        return List.of(createdAt)
                .appendAll(committed)
                .appendAll(forcePushed)
                .max()
                .get();
    }

    private static Option<ReviewerState> reviewerState(String state) {
        return switch (state) {
            case "APPROVED" -> Option.of(ReviewerState.APPROVED);
            case "CHANGES_REQUESTED" -> Option.of(ReviewerState.CHANGES_REQUESTED);
            case "COMMENTED" -> Option.of(ReviewerState.COMMENTED);
            // Dismissed, or not submitted yet.
            default -> Option.none();
        };
    }

    private static Mergeable mergeable(String state) {
        return switch (state) {
            case "MERGEABLE" -> Mergeable.MERGEABLE;
            case "CONFLICTING" -> Mergeable.CONFLICTING;
            default -> Mergeable.UNKNOWN;
        };
    }
}
