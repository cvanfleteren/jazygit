package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;

/**
 * The coloured symbols for how the checks and the review of a pull request are doing, the same wherever
 * they show.
 */
public final class PullRequestSymbols {

    private static final Symbol PASSED = new Symbol("✅", Theme.Classes.CHECKS_PASSED);
    private static final Symbol FAILED = new Symbol("❌", Theme.Classes.CHECKS_FAILED);
    private static final Symbol RUNNING = new Symbol("⏳", Theme.Classes.CHECKS_RUNNING);
    // Not ⏸, which most terminals show one column wide, and some two.
    private static final Symbol QUEUED = new Symbol("💤", Theme.Classes.DIM);
    private static final Symbol APPROVED = new Symbol("👍", Theme.Classes.REVIEW_APPROVED);
    private static final Symbol CHANGES = new Symbol("👎", Theme.Classes.REVIEW_CHANGES);
    private static final Symbol TO_REVIEW = new Symbol("?", Theme.Classes.DIM);
    private static final Symbol COMMENTED = new Symbol("💬", Theme.Classes.DIM);
    private static final Symbol DISMISSED = new Symbol("⊘", Theme.Classes.DIM);
    private static final Symbol MERGING = new Symbol("🔀", Theme.Classes.DIM);
    private static final Symbol ENQUEUED = new Symbol("🚦", Theme.Classes.DIM);
    private static final Symbol MERGE_FAILED = new Symbol("💥", Theme.Classes.CHECKS_FAILED);
    private static final Symbol MERGED = new Symbol("🟣", Theme.Classes.PULL_REQUEST);

    private PullRequestSymbols() {}

    /**
     * How a merge of a pull request goes, which shows instead of its checks and its review (ADR 24).
     */
    public static Symbol merge(MergeStatus status) {
        return switch (status) {
            case MergeStatus.Pending pending -> MERGING;
            case MergeStatus.Enqueued enqueued -> ENQUEUED;
            case MergeStatus.Failed failed -> MERGE_FAILED;
            case MergeStatus.Merged merged -> MERGED;
        };
    }

    /**
     * Where a pull request sits in its stack: {@code 📚2/3}.
     */
    public static Symbol stack(PullRequestStack stack) {
        return new Symbol("📚" + stack.position() + "/" + stack.size(), Theme.Classes.DIM);
    }

    /**
     * What shows of a pull request after its number: where it sits in its stack, if it is stacked; then how its
     * merge goes, while one goes or went, else how its checks are doing and where its review stands.
     */
    public static List<Symbol> state(
            Option<PullRequestStack> stack, Checks checks, Review review, Option<MergeStatus> merge) {
        return state(stack, checks, Option.none(), review, merge);
    }

    /**
     * As {@link #state(Option, Checks, Review, Option)}, with {@code eta}, when the checks are expected to be done,
     * right after the symbol of the checks while they run (ADR 32).
     */
    public static List<Symbol> state(
            Option<PullRequestStack> stack,
            Checks checks,
            Option<Symbol> eta,
            Review review,
            Option<MergeStatus> merge) {
        return stack.map(PullRequestSymbols::stack)
                .toList()
                .appendAll(merge.map(status -> List.of(merge(status)))
                        .getOrElse(() -> checks(checks)
                                .toList()
                                .appendAll(eta.filter(symbol -> checks == Checks.PENDING))
                                .appendAll(review(review))));
    }

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
     * How one check is doing: as {@link #checks(Checks)}, but waiting to start while it is queued (ADR 32).
     */
    public static Option<Symbol> check(Check check) {
        return check.queued() ? Option.of(QUEUED) : checks(check.state());
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
     * How a reviewer's review went, or that it is still to do.
     */
    public static Symbol reviewer(ReviewerState state) {
        return switch (state) {
            case APPROVED -> APPROVED;
            case CHANGES_REQUESTED -> CHANGES;
            case REQUESTED -> TO_REVIEW;
            case COMMENTED -> COMMENTED;
        };
    }

    /**
     * A symbol, in the colour of {@code cssClass}.
     */
    public record Symbol(String text, String cssClass) {}
}
