package net.vanfleteren.jazygit.gh.model;

/**
 * What a review says of a pull request, as GraphQL's {@code PullRequestReviewEvent} names it (ADR 99).
 */
public enum Verdict {
    APPROVE,
    REQUEST_CHANGES;

    /**
     * Whether GitHub needs a text with it: to ask for changes, not to approve.
     */
    public boolean needsText() {
        return this == REQUEST_CHANGES;
    }
}
