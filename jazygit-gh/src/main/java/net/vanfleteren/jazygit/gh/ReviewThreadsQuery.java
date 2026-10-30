package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
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

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The review threads of one pull request, all pages of them, with the commits its changes are diffed between
 * (ADR 55).
 */
final class ReviewThreadsQuery {

    // The most comments read of a thread; it says how many more it has.
    static final int COMMENTS = 50;

    /*
     * gh fills in the repository of the working tree for {owner} and {repo}, and $endCursor while it reads the pages.
     */
    static final String QUERY = """
            query($owner: String!, $name: String!, $number: Int!, $endCursor: String) { \
            repository(owner: $owner, name: $name) { pullRequest(number: $number) { id state headRefOid baseRefOid \
            reviewThreads(first: 100, after: $endCursor) { pageInfo { hasNextPage endCursor } \
            nodes { id path diffSide line startLine startDiffSide originalLine isResolved isOutdated subjectType \
            viewerCanReply viewerCanResolve viewerCanUnresolve \
            comments(first: %d) { totalCount nodes { id author { login } createdAt body state isMinimized \
            minimizedReason } } } } } } }""".formatted(COMMENTS);

    // Who wrote it; empty when GitHub does not say, as for a deleted account.
    static final Decoder<String> AUTHOR = field("author", nullable(field("login", nullable(string()))))
            .map(author -> author.flatMap(login -> login).getOrElse(""));

    // A comment; none for one of a review that is not submitted yet, which only its author sees.
    static final Decoder<Option<Comment>> COMMENT = map4(
            map3(AUTHOR, field("createdAt", instant()), field("body", string()), Tuple::of),
            field("state", string()),
            map2(field("isMinimized", bool()), field("minimizedReason", nullable(string())), Tuple::of),
            field("id", string()),
            (written, state, hidden, id) -> Option.when(
                    !state.equals("PENDING"),
                    () -> new Comment(
                            written._1(),
                            written._2(),
                            written._3(),
                            Option.when(hidden._1(), () -> hidden._2().getOrElse("")),
                            id)));

    // What the viewer may do with a thread.
    static final Decoder<ReviewThread.Rights> RIGHTS = map3(
            field("viewerCanReply", bool()),
            field("viewerCanResolve", bool()),
            field("viewerCanUnresolve", bool()),
            ReviewThread.Rights::new);

    // The comments read of a thread, and how many more it has.
    static final Decoder<Tuple2<List<Comment>, Integer>> COMMENTS_READ = field(
            "comments",
            map2(
                    field("totalCount", integer()),
                    field("nodes", list(COMMENT)),
                    (total, nodes) -> Tuple.of(nodes.flatMap(comment -> comment), Math.max(0, total - nodes.size()))));

    // A thread; none for one with only comments of a review that is not submitted yet.
    static final Decoder<Option<ReviewThread>> THREAD = map5(
            map8(
                    field("path", string()),
                    field("diffSide", enumOf(Side.class)),
                    field("line", nullable(integer())),
                    field("startLine", nullable(integer())),
                    field("originalLine", nullable(integer())),
                    field("isResolved", bool()),
                    field("isOutdated", bool()),
                    field("subjectType", enumOf(Subject.class)),
                    Tuple::of),
            // The side of its first line, none when it is on one line; it is then that of its line.
            field("startDiffSide", nullable(enumOf(Side.class))),
            COMMENTS_READ,
            field("id", string()),
            RIGHTS,
            (thread, startSide, comments, id, rights) -> Option.when(
                    !comments._1().isEmpty(),
                    () -> new ReviewThread(
                            thread._1(),
                            thread._2(),
                            thread._3(),
                            thread._4(),
                            startSide.getOrElse(thread._2()),
                            thread._5(),
                            thread._6(),
                            thread._7(),
                            thread._8(),
                            comments._1(),
                            comments._2(),
                            id,
                            rights)));

    // One page: its state, the commits, and its threads.
    static final Decoder<ReviewRemarks> PAGE = at(
            "data.repository.pullRequest",
            map5(
                    field("state", enumOf(PullRequestDetails.State.class)),
                    field("headRefOid", nullable(string())).map(oid -> oid.flatMap(GhCommit::parse)),
                    field("baseRefOid", nullable(string())).map(oid -> oid.flatMap(GhCommit::parse)),
                    at("reviewThreads.nodes", list(THREAD)),
                    field("id", string()),
                    (state, head, base, threads, id) ->
                            new ReviewRemarks(state, head, base, threads.flatMap(thread -> thread), id)));

    // All pages, as one array: the state and the commits as the first page has them, and the threads of all.
    static final Decoder<ReviewRemarks> DECODER = list(PAGE)
            .map(pages -> pages.headOption()
                    .map(first -> new ReviewRemarks(
                            first.state(),
                            first.head(),
                            first.base(),
                            pages.flatMap(ReviewRemarks::threads),
                            first.id()))
                    .getOrElse(ReviewRemarks::none));

    private ReviewThreadsQuery() {}

    static GhCommand command(int number) {
        return gh(
                "api",
                "graphql",
                "--paginate",
                "--slurp",
                "-F",
                "owner={owner}",
                "-F",
                "name={repo}",
                "-F",
                "number=" + number,
                "-f",
                "query=" + QUERY);
    }
}
