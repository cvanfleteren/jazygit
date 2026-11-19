package net.vanfleteren.jazygit.gh.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import org.junit.jupiter.api.Test;

/**
 * Where the review of a pull request stands, as it is decoded.
 */
class ReviewingFragmentTest {

    @Test
    void readsTheReviewDecision() {
        assertEquals(Review.REQUIRED, ReviewingFragment.review("REVIEW_REQUIRED"));
        assertEquals(Review.APPROVED, ReviewingFragment.review("APPROVED"));
        assertEquals(Review.CHANGES_REQUESTED, ReviewingFragment.review("CHANGES_REQUESTED"));
        assertEquals(Review.NONE, ReviewingFragment.review(""));
    }
}
