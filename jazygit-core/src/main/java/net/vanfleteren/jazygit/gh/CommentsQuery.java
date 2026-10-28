package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;
import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.fail;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.instant;
import static net.vanfleteren.jazygit.json.Decoder.list;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.map5;
import static net.vanfleteren.jazygit.json.Decoder.map7;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;
import static net.vanfleteren.jazygit.json.Decoder.succeed;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestComment.Kind;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * The conversation on a pull request, read with {@code gh pr view --json}: its description first, then its comments
 * and reviews, the oldest first (ADR 6, 42).
 */
final class CommentsQuery {

    // Who wrote it; empty when GitHub does not say, as for a deleted account.
    static final Decoder<String> AUTHOR = field("author", nullable(field("login", nullable(string()))))
            .map(author -> author.flatMap(login -> login).getOrElse(""));

    // The description, none when it has no text.
    static final Decoder<Option<PullRequestComment>> DESCRIPTION = map3(
            field("body", string()),
            AUTHOR,
            field("createdAt", instant()),
            (body, author, at) -> Option.when(
                    !body.isEmpty(),
                    () -> new PullRequestComment(
                            PullRequestComment.DESCRIPTION, Kind.DESCRIPTION, author, at, body, false, Option.none())));

    // A comment, hidden for the reason GitHub gives when it is minimized.
    static final Decoder<PullRequestComment> COMMENT = map7(
            field("id", string()),
            AUTHOR,
            field("createdAt", instant()),
            field("includesCreatedEdit", bool()),
            field("isMinimized", bool()),
            field("minimizedReason", nullable(string())),
            field("body", string()),
            (id, author, at, edited, minimized, reason, body) -> new PullRequestComment(
                    new PullRequestComment.Id(id),
                    Kind.COMMENT,
                    author,
                    at,
                    body,
                    edited,
                    Option.when(minimized, () -> reason.getOrElse(""))));

    /*
     * A review; none for a pending one, which is not submitted yet, and for one that only commented and has no text,
     * which is what inline comments hang from.
     */
    static final Decoder<Option<PullRequestComment>> REVIEW = field("state", string())
            .flatMap(state -> state.equals("PENDING")
                    ? succeed(Option.<PullRequestComment>none())
                    : kind(state)
                            .flatMap(kind -> map5(
                                    field("id", string()),
                                    AUTHOR,
                                    field("submittedAt", instant()),
                                    field("includesCreatedEdit", bool()),
                                    field("body", string()),
                                    (id, author, at, edited, body) -> Option.when(
                                            !body.isEmpty() || kind != Kind.COMMENTED,
                                            () -> new PullRequestComment(
                                                    new PullRequestComment.Id(id),
                                                    kind,
                                                    author,
                                                    at,
                                                    body,
                                                    edited,
                                                    Option.none())))));

    static final Decoder<List<PullRequestComment>> DECODER = map3(
            DESCRIPTION,
            field("comments", list(COMMENT)),
            field("reviews", list(REVIEW)),
            (description, comments, reviews) -> description
                    .toList()
                    .appendAll(comments.appendAll(reviews.flatMap(review -> review))
                            .sortBy(PullRequestComment::at)));

    private CommentsQuery() {}

    static GhCommand command(int number) {
        return gh("pr", "view", String.valueOf(number), "--json", "body,author,createdAt,comments,reviews");
    }

    // What a review of state did; a failure for a state that is not known.
    private static Decoder<Kind> kind(String state) {
        return switch (state) {
            case "APPROVED" -> succeed(Kind.APPROVED);
            case "CHANGES_REQUESTED" -> succeed(Kind.CHANGES_REQUESTED);
            case "COMMENTED" -> succeed(Kind.COMMENTED);
            case "DISMISSED" -> succeed(Kind.DISMISSED);
            default ->
                fail("expected APPROVED, CHANGES_REQUESTED, COMMENTED, DISMISSED or PENDING, got \"" + state + "\"");
        };
    }
}
