package net.vanfleteren.jazygit.gh.reviews;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.at;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.enumOf;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.integer;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map2;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map5;
import static net.vanfleteren.jazygit.json.Decoder.map6;
import static net.vanfleteren.jazygit.json.Decoder.map8;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.graphql.ReviewingFragment;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.Reactions;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.gh.process.GhCommand;
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
            repository(owner: $owner, name: $name) { pullRequest(number: $number) { state headRefOid baseRefOid \
            isDraft id viewerDidAuthor viewerLatestReview { state } \
            reviewThreads(first: 100, after: $endCursor) { pageInfo { hasNextPage endCursor } \
            nodes { id path diffSide line startLine startDiffSide originalLine isResolved isOutdated subjectType \
            viewerCanReply viewerCanResolve viewerCanUnresolve \
            comments(first: %d) { totalCount nodes { id fullDatabaseId author { login } createdAt body state isMinimized \
            minimizedReason viewerCanUpdate viewerCanDelete viewerCanReact \
            reactionGroups { content viewerHasReacted reactors { totalCount } } } } } } } } }""".formatted(COMMENTS);

    // Who wrote it; empty when GitHub does not say, as for a deleted account.
    static final Decoder<String> AUTHOR = field("author", nullable(field("login", nullable(string()))))
            .map(author -> author.flatMap(login -> login).getOrElse(""));

    // What the viewer may do with a comment.
    static final Decoder<CommentRights> COMMENT_RIGHTS = map3(
            field("viewerCanUpdate", bool()),
            field("viewerCanDelete", bool()),
            field("viewerCanReact", bool()),
            CommentRights::new);

    // One reaction: GitHub lists each kind, also those nobody gave. None for a kind jazygit does not know, as one
    // GitHub adds later: an unknown kind is left out rather than failing the read of every thread (ADR 86).
    static final Decoder<Option<Reactions.Group>> REACTION_GROUP = map3(
            field("content", string()).map(ReviewThreadsQuery::reaction),
            at("reactors.totalCount", integer()),
            field("viewerHasReacted", bool()),
            (reaction, count, viewer) -> reaction.map(known -> new Reactions.Group(known, count, viewer)));

    // The reactions given to a comment, in the order GitHub's site shows them; none when GitHub does not say.
    static final Decoder<Reactions> REACTIONS = field("reactionGroups", nullable(list(REACTION_GROUP)))
            .map(groups -> new Reactions(groups.getOrElse(List.empty())
                    .flatMap(group -> group)
                    .filter(group -> group.count() > 0)
                    .sortBy(group -> group.reaction().ordinal())));

    // A comment; none for one of a review that is not submitted yet, which only its author sees.
    static final Decoder<Option<Comment>> COMMENT = map5(
            map3(AUTHOR, field("createdAt", instant()), field("body", string()), Tuple::of),
            field("state", string()),
            map2(field("isMinimized", bool()), field("minimizedReason", nullable(string())), Tuple::of),
            map3(
                    field("id", string()).map(Comment.Id::new),
                    field("fullDatabaseId", string()).map(Comment.RestId::new),
                    COMMENT_RIGHTS,
                    Tuple::of),
            REACTIONS,
            (written, state, hidden, ids, reactions) -> Option.when(
                    !state.equals("PENDING"),
                    () -> new Comment(
                            written._1(),
                            written._2(),
                            written._3(),
                            Option.when(hidden._1(), () -> hidden._2().getOrElse("")),
                            ids._1(),
                            ids._2(),
                            ids._3(),
                            reactions)));

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
            field("id", string()).map(ReviewThread.Id::new),
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

    // One page: its state, the commits, its threads, and what the viewer needs to review it (ADR 100).
    static final Decoder<ReviewRemarks> PAGE = at(
            "data.repository.pullRequest",
            map6(
                    field("state", enumOf(PullRequestDetails.State.class)),
                    field("headRefOid", nullable(string())).map(oid -> oid.flatMap(GhCommit::parse)),
                    field("baseRefOid", nullable(string())).map(oid -> oid.flatMap(GhCommit::parse)),
                    at("reviewThreads.nodes", list(THREAD)),
                    field("isDraft", bool()),
                    ReviewingFragment.REVIEWING,
                    (state, head, base, threads, draft, reviewing) ->
                            new ReviewRemarks(state, head, base, threads.flatMap(thread -> thread), draft, reviewing)));

    // All pages, as one array: the pull request as the first page has it, and the threads of all.
    static final Decoder<ReviewRemarks> DECODER = list(PAGE)
            .map(pages -> pages.headOption()
                    .map(first -> first.withThreads(pages.flatMap(ReviewRemarks::threads)))
                    .getOrElse(ReviewRemarks::none));

    private ReviewThreadsQuery() {}

    // The reaction GitHub names name, if jazygit knows it.
    private static Option<Reaction> reaction(String name) {
        return List.of(Reaction.values()).find(reaction -> reaction.name().equals(name));
    }

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
