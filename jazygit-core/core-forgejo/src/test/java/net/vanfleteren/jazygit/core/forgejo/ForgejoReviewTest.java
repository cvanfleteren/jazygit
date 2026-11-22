package net.vanfleteren.jazygit.core.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview.Verdict;
import org.junit.jupiter.api.Test;

/**
 * Where the review of a pull request stands, by its reviews (ADR 176).
 */
class ForgejoReviewTest {

    private static ForgejoReview review(String reviewer, Verdict verdict) {
        return new ForgejoReview(reviewer, verdict, false, false);
    }

    @Test
    void changesRequestedByOneReviewerStandAboveAnotherOnesApproval() {
        assertEquals(
                Option.of(Verdict.CHANGES_REQUESTED),
                ForgejoReview.standing(
                        List.of(review("anna", Verdict.APPROVED), review("bob", Verdict.CHANGES_REQUESTED))));
    }

    @Test
    void aReviewersLastVerdictCounts() {
        assertEquals(
                Option.of(Verdict.CHANGES_REQUESTED),
                ForgejoReview.standing(
                        List.of(review("anna", Verdict.APPROVED), review("anna", Verdict.CHANGES_REQUESTED))));
    }

    @Test
    void aDismissedReviewACommentOrAPendingOneDoesNotCount() {
        assertEquals(
                Option.none(),
                ForgejoReview.standing(List.of(
                        new ForgejoReview("anna", Verdict.CHANGES_REQUESTED, false, true),
                        review("bob", Verdict.COMMENTED),
                        review("carl", Verdict.PENDING))));
        assertEquals(Option.none(), ForgejoReview.standing(List.empty()));
    }
}
