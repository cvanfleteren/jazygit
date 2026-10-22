package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;

/**
 * The coloured symbols for how the checks and the review of a pull request are doing, the same wherever
 * they show.
 */
public final class PullRequestSymbols {

    private static final Symbol PASSED = new Symbol("✓", Theme.Classes.CHECKS_PASSED);
    private static final Symbol FAILED = new Symbol("✗", Theme.Classes.CHECKS_FAILED);
    private static final Symbol RUNNING = new Symbol("●", Theme.Classes.CHECKS_RUNNING);
    private static final Symbol APPROVED = new Symbol("👍", Theme.Classes.REVIEW_APPROVED);
    private static final Symbol CHANGES = new Symbol("✎", Theme.Classes.REVIEW_CHANGES);
    private static final Symbol TO_REVIEW = new Symbol("?", Theme.Classes.DIM);
    private static final Symbol COMMENTED = new Symbol("●", Theme.Classes.DIM);
    private static final Symbol DISMISSED = new Symbol("⊘", Theme.Classes.DIM);

    private PullRequestSymbols() {}

    /**
     * What a part of the conversation is: nothing for the description and a comment, else how the review went.
     */
    public static Option<Symbol> comment(PullRequestComment.Kind kind) {
        return switch (kind) {
            case DESCRIPTION, COMMENT -> Option.none();
            case APPROVED -> Option.of(APPROVED);
            case CHANGES_REQUESTED -> Option.of(CHANGES);
            case COMMENTED -> Option.of(COMMENTED);
            case DISMISSED -> Option.of(DISMISSED);
        };
    }

    /**
     * Nothing for no checks.
     */
    public static Option<Symbol> checks(Checks checks) {
        return switch (checks) {
            case NONE -> Option.none();
            case PASSING -> Option.of(PASSED);
            case FAILING -> Option.of(FAILED);
            case PENDING -> Option.of(RUNNING);
        };
    }

    /**
     * Nothing when no review is needed.
     */
    public static Option<Symbol> review(Review review) {
        return switch (review) {
            case NONE -> Option.none();
            case APPROVED -> Option.of(APPROVED);
            case CHANGES_REQUESTED -> Option.of(CHANGES);
            case REQUIRED -> Option.of(TO_REVIEW);
        };
    }

    /**
     * Nothing for a reviewer who only commented.
     */
    public static Option<Symbol> reviewer(ReviewerState state) {
        return switch (state) {
            case APPROVED -> Option.of(APPROVED);
            case CHANGES_REQUESTED -> Option.of(CHANGES);
            case REQUESTED -> Option.of(TO_REVIEW);
            case COMMENTED -> Option.none();
        };
    }

    /**
     * A symbol, in the colour of {@code cssClass}.
     */
    public record Symbol(String text, String cssClass) {}
}
