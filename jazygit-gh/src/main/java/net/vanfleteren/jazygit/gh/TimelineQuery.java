package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.byField;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map4;
import static net.vanfleteren.jazygit.json.Decoder.map5;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.optionalField;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestEvent;
import net.vanfleteren.jazygit.gh.model.PullRequestEvent.Kind;
import net.vanfleteren.jazygit.gh.model.PullRequestEvent.Remark;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * PROTOTYPE (branch prototype/pr-panel): the last things that happened on a pull request of any repository, oldest
 * first: comments, reviews with their comments on lines, commits, force pushes, review requests, labels, and drafts,
 * merges and closes.
 */
final class TimelineQuery {

    static final String QUERY = """
            query($owner: String!, $name: String!, $number: Int!) { \
            repository(owner: $owner, name: $name) { pullRequest(number: $number) { \
            timelineItems(last: 80, itemTypes: [ISSUE_COMMENT, PULL_REQUEST_REVIEW, PULL_REQUEST_COMMIT, \
            HEAD_REF_FORCE_PUSHED_EVENT, REVIEW_REQUESTED_EVENT, READY_FOR_REVIEW_EVENT, CONVERT_TO_DRAFT_EVENT, \
            LABELED_EVENT, MERGED_EVENT, CLOSED_EVENT, REOPENED_EVENT]) { nodes { __typename \
            ... on IssueComment { createdAt author { login } body } \
            ... on PullRequestReview { createdAt author { login } state body \
            comments(first: 30) { nodes { path line body } } } \
            ... on PullRequestCommit { commit { committedDate messageHeadline abbreviatedOid \
            author { name user { login } } } } \
            ... on HeadRefForcePushedEvent { createdAt actor { login } } \
            ... on ReviewRequestedEvent { createdAt actor { login } requestedReviewer { __typename \
            ... on User { login } ... on Team { name } ... on Bot { login } } } \
            ... on ReadyForReviewEvent { createdAt actor { login } } \
            ... on ConvertToDraftEvent { createdAt actor { login } } \
            ... on LabeledEvent { createdAt actor { login } label { name } } \
            ... on MergedEvent { createdAt actor { login } } \
            ... on ClosedEvent { createdAt actor { login } } \
            ... on ReopenedEvent { createdAt actor { login } } } } } } }""";

    private static Decoder<String> login(String name) {
        return optionalField(name, nullable(field("login", string())))
                .map(login -> login.flatMap(read -> read).getOrElse(""));
    }

    private static Decoder<PullRequestEvent> event(Kind kind, Decoder<String> subject) {
        return map3(
                field("createdAt", instant()),
                login("actor"),
                subject,
                (at, actor, about) -> new PullRequestEvent(at, actor, kind, about, "", List.empty()));
    }

    private static final Decoder<PullRequestEvent> COMMENT = map3(
            field("createdAt", instant()),
            login("author"),
            field("body", string()),
            (at, author, body) -> new PullRequestEvent(at, author, Kind.COMMENTED, "", body, List.empty()));

    private static final Decoder<Remark> REMARK = map3(
            field("path", string()),
            field("line", nullable(integer())),
            field("body", string()),
            Remark::new);

    private static final Decoder<PullRequestEvent> REVIEW = map5(
            field("createdAt", instant()),
            login("author"),
            field("state", string()),
            field("body", string()),
            at("comments.nodes", list(REMARK)),
            (at, author, state, body, remarks) -> new PullRequestEvent(
                    at,
                    author,
                    switch (state) {
                        case "APPROVED" -> Kind.APPROVED;
                        case "CHANGES_REQUESTED" -> Kind.CHANGES_REQUESTED;
                        default -> Kind.REVIEWED;
                    },
                    "",
                    body,
                    remarks));

    private static final Decoder<PullRequestEvent> COMMIT = field(
            "commit",
            map4(
                    field("committedDate", instant()),
                    field("author", map2(
                            field("name", nullable(string())),
                            login("user"),
                            (name, user) -> user.isEmpty() ? name.getOrElse("") : user)),
                    field("abbreviatedOid", string()),
                    field("messageHeadline", string()),
                    (at, author, oid, headline) ->
                            new PullRequestEvent(at, author, Kind.COMMITTED, oid + " " + headline, "", List.empty())));

    private static final Decoder<String> REVIEWER = field(
            "requestedReviewer",
            nullable(json -> optionalField("login", string())
                            .decode(json)
                            .flatMap(login -> login.isDefined()
                                    ? io.vavr.control.Either.right(login.get())
                                    : optionalField("name", string()).decode(json).map(name -> name.getOrElse("")))))
            .map(reviewer -> reviewer.getOrElse(""));

    private static final Decoder<Option<PullRequestEvent>> ITEM = byField(
            "__typename",
            HashMap.of(
                            "IssueComment", COMMENT,
                            "PullRequestReview", REVIEW,
                            "PullRequestCommit", COMMIT,
                            "HeadRefForcePushedEvent", event(Kind.FORCE_PUSHED, succeed("")),
                            "ReviewRequestedEvent", event(Kind.REVIEW_REQUESTED, REVIEWER),
                            "ReadyForReviewEvent", event(Kind.READY_FOR_REVIEW, succeed("")),
                            "ConvertToDraftEvent", event(Kind.CONVERTED_TO_DRAFT, succeed("")),
                            "LabeledEvent", event(Kind.LABELED, at("label.name", string())),
                            "MergedEvent", event(Kind.MERGED, succeed("")))
                    .put("ClosedEvent", event(Kind.CLOSED, succeed("")))
                    .put("ReopenedEvent", event(Kind.REOPENED, succeed("")))
                    .mapValues(decoder -> decoder.map(Option::some)),
            succeed(Option.<PullRequestEvent>none()));

    static final Decoder<List<PullRequestEvent>> DECODER = at(
            "data.repository.pullRequest.timelineItems.nodes",
            list(ITEM).map(items -> items.flatMap(item -> item)));

    private TimelineQuery() {}

    static GhCommand command(String repository, int number) {
        String[] parts = repository.split("/", 2);
        return gh(
                "api",
                "graphql",
                "-F",
                "owner=" + parts[0],
                "-F",
                "name=" + parts[1],
                "-F",
                "number=" + number,
                "-f",
                "query=" + QUERY);
    }
}
