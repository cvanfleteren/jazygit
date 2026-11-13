package net.vanfleteren.jazygit.gh.graphql;

import static net.vanfleteren.jazygit.json.Decoder.bool;
import static net.vanfleteren.jazygit.json.Decoder.field;
import static net.vanfleteren.jazygit.json.Decoder.map3;
import static net.vanfleteren.jazygit.json.Decoder.nullable;
import static net.vanfleteren.jazygit.json.Decoder.string;

import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewing;
import net.vanfleteren.jazygit.json.Decoder;

/**
 * Where the review of a pull request stands, as it is decoded: its review decision, which a list of pull requests and
 * the details show, and what the viewer needs to review it, which the details and the review threads read.
 */
public final class ReviewingFragment {

    // The review decision on it; none when GitHub has none.
    public static final Decoder<Review> DECISION =
            field("reviewDecision", nullable(string())).map(decision -> review(decision.getOrElse("")));

    // Its node id, whether it is the viewer's, and whether they have a pending review.
    public static final Decoder<Reviewing> REVIEWING = map3(
            field("id", string()),
            field("viewerDidAuthor", bool()),
            field("viewerLatestReview", nullable(field("state", string()))).map(state -> state.contains("PENDING")),
            Reviewing::new);

    private ReviewingFragment() {}

    static Review review(String decision) {
        return switch (decision) {
            case "APPROVED" -> Review.APPROVED;
            case "CHANGES_REQUESTED" -> Review.CHANGES_REQUESTED;
            case "REVIEW_REQUIRED" -> Review.REQUIRED;
            default -> Review.NONE;
        };
    }
}
