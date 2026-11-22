package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.Locale;

/**
 * A review of a pull request on Forgejo (ADR 176).
 *
 * @param reviewer  the login of who reviewed it; empty when that is not known
 * @param verdict   what they said of it
 * @param stale     whether it was given before the pull request last changed
 * @param dismissed whether it was set aside
 */
public record ForgejoReview(String reviewer, Verdict verdict, boolean stale, boolean dismissed) {

    /**
     * What a review says: Forgejo's {@code APPROVED}, {@code REQUEST_CHANGES}, {@code COMMENT}, {@code PENDING} (not
     * sent yet) and {@code REQUEST_REVIEW} (asked for); any other is a comment.
     */
    public enum Verdict {
        APPROVED,
        CHANGES_REQUESTED,
        COMMENTED,
        PENDING,
        REQUESTED;

        public static Verdict named(String name) {
            return switch (name.toUpperCase(Locale.ROOT)) {
                case "APPROVED" -> APPROVED;
                case "REQUEST_CHANGES" -> CHANGES_REQUESTED;
                case "PENDING" -> PENDING;
                case "REQUEST_REVIEW" -> REQUESTED;
                default -> COMMENTED;
            };
        }
    }

    /**
     * Where the review of a pull request stands, by {@code reviews}: changes requested when a reviewer's last
     * verdict that counts asks for them, else approved when one approves; none otherwise. A review that is pending,
     * dismissed, or only a comment does not count. The reviews are in the order they were given.
     */
    public static Option<Verdict> standing(List<ForgejoReview> reviews) {
        List<Verdict> last = reviews.filter(ForgejoReview::counts)
                .groupBy(ForgejoReview::reviewer)
                .values()
                .map(byReviewer -> byReviewer.last().verdict())
                .toList();
        return last.contains(Verdict.CHANGES_REQUESTED)
                ? Option.of(Verdict.CHANGES_REQUESTED)
                : Option.when(last.contains(Verdict.APPROVED), Verdict.APPROVED);
    }

    /**
     * Whether it says something of the pull request: it approves or asks for changes, and was not set aside.
     */
    public boolean counts() {
        return !dismissed && (verdict == Verdict.APPROVED || verdict == Verdict.CHANGES_REQUESTED);
    }
}
