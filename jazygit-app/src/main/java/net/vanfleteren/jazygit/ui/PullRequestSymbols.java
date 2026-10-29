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

    /**
     * Symbols one column wide, told apart by their shape as well as their colour (ADR 45), wherever they show
     * (ADR 48).
     */
    public static final Glyphs GLYPHS = new Glyphs(
            new Symbol("✓", Theme.Classes.CHECKS_PASSED),
            new Symbol("✗", Theme.Classes.CHECKS_FAILED),
            new Symbol("●", Theme.Classes.CHECKS_RUNNING),
            new Symbol("◌", Theme.Classes.DIM),
            new Symbol("+", Theme.Classes.REVIEW_APPROVED),
            new Symbol("−", Theme.Classes.REVIEW_CHANGES),
            new Symbol("?", Theme.Classes.DIM),
            new Symbol("»", Theme.Classes.DIM),
            new Symbol("≡", Theme.Classes.DIM),
            new Symbol("!", Theme.Classes.CHECKS_FAILED),
            new Symbol("◆", Theme.Classes.PULL_REQUEST),
            "");

    private static final Symbol COMMENTED = new Symbol("~", Theme.Classes.DIM);
    private static final Symbol DISMISSED = new Symbol("⊘", Theme.Classes.DIM);

    private PullRequestSymbols() {}

    /**
     * How a merge of a pull request goes, which shows instead of its checks and its review (ADR 24).
     */
    public static Symbol merge(MergeStatus status) {
        return GLYPHS.merge(status);
    }

    /**
     * Where a pull request sits in its stack: {@code 2/3}.
     */
    public static Symbol stack(PullRequestStack stack) {
        return GLYPHS.stack(stack);
    }

    /**
     * What shows of a pull request after its number: where it sits in its stack, if it is stacked; then how its
     * merge goes, while one goes or went, else how its checks are doing and where its review stands.
     */
    public static List<Symbol> state(
            Option<PullRequestStack> stack, Checks checks, Review review, Option<MergeStatus> merge) {
        return stack.map(PullRequestSymbols::stack)
                .toList()
                .appendAll(merge.map(status -> List.of(merge(status)))
                        .getOrElse(() -> checks(checks).toList().appendAll(review(review))));
    }

    /**
     * What a part of the conversation is: nothing for the description and a comment, else how the review went.
     */
    public static Option<Symbol> comment(PullRequestComment.Kind kind) {
        return switch (kind) {
            case DESCRIPTION, COMMENT -> Option.none();
            case APPROVED -> Option.of(GLYPHS.approved());
            case CHANGES_REQUESTED -> Option.of(GLYPHS.changes());
            case COMMENTED -> Option.of(COMMENTED);
            case DISMISSED -> Option.of(DISMISSED);
        };
    }

    /**
     * Nothing for no checks.
     */
    public static Option<Symbol> checks(Checks checks) {
        return GLYPHS.checks(checks);
    }

    /**
     * How one check is doing: as {@link #checks(Checks)}, but waiting to start while it is queued (ADR 32).
     */
    public static Option<Symbol> check(Check check) {
        return check.queued() ? Option.of(GLYPHS.queued()) : checks(check.state());
    }

    /**
     * Nothing when no review is needed.
     */
    public static Option<Symbol> review(Review review) {
        return GLYPHS.review(review);
    }

    /**
     * How a reviewer's review went, or that it is still to do.
     */
    public static Symbol reviewer(ReviewerState state) {
        return switch (state) {
            case APPROVED -> GLYPHS.approved();
            case CHANGES_REQUESTED -> GLYPHS.changes();
            case REQUESTED -> GLYPHS.toReview();
            case COMMENTED -> COMMENTED;
        };
    }

    /**
     * A symbol, in the colour of {@code cssClass}.
     */
    public record Symbol(String text, String cssClass) {}

    /**
     * A symbol for each state of the checks, the review and the merge of a pull request, and what goes before
     * where it sits in its stack.
     */
    public record Glyphs(
            Symbol passed,
            Symbol failed,
            Symbol running,
            Symbol queued,
            Symbol approved,
            Symbol changes,
            Symbol toReview,
            Symbol merging,
            Symbol enqueued,
            Symbol mergeFailed,
            Symbol merged,
            String stackPrefix) {

        /**
         * How a merge of a pull request goes (ADR 24).
         */
        public Symbol merge(MergeStatus status) {
            return switch (status) {
                case MergeStatus.Pending pending -> merging;
                case MergeStatus.Enqueued queue -> enqueued;
                case MergeStatus.Failed failure -> mergeFailed;
                case MergeStatus.Merged done -> merged;
            };
        }

        /**
         * Where a pull request sits in its stack, dimmed.
         */
        public Symbol stack(PullRequestStack stack) {
            return new Symbol(stackPrefix + stack.position() + "/" + stack.size(), Theme.Classes.DIM);
        }

        /**
         * Nothing for no checks.
         */
        public Option<Symbol> checks(Checks checks) {
            return switch (checks) {
                case NONE -> Option.none();
                case PASSING -> Option.of(passed);
                case FAILING -> Option.of(failed);
                case PENDING -> Option.of(running);
            };
        }

        /**
         * As {@link #checks(Checks)}, but waiting to start when they run and {@code waiting}: they all wait
         * (ADR 35).
         */
        public Option<Symbol> checks(Checks checks, boolean waiting) {
            return waiting && checks == Checks.PENDING ? Option.of(queued) : checks(checks);
        }

        /**
         * Nothing when no review is needed.
         */
        public Option<Symbol> review(Review review) {
            return switch (review) {
                case NONE -> Option.none();
                case APPROVED -> Option.of(approved);
                case CHANGES_REQUESTED -> Option.of(changes);
                case REQUIRED -> Option.of(toReview);
            };
        }
    }
}
