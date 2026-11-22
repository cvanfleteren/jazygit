package net.vanfleteren.jazygit.core.forgejo;

/**
 * How Forgejo merges a pull request into its base (ADR 180), as its API names the style ({@code Do}).
 */
public enum ForgejoMergeStyle {
    /**
     * A merge commit.
     */
    MERGE("merge"),
    /**
     * All its commits as one commit on the base.
     */
    SQUASH("squash"),
    /**
     * Its commits put on the base one by one, without a merge commit.
     */
    REBASE("rebase"),
    /**
     * Its commits put on the base, then a merge commit.
     */
    REBASE_MERGE("rebase-merge"),
    /**
     * The base moved to its head, which only goes when the base is in it.
     */
    FAST_FORWARD_ONLY("fast-forward-only");

    private final String value;

    ForgejoMergeStyle(String value) {
        this.value = value;
    }

    /**
     * The style as Forgejo's API names it.
     */
    public String value() {
        return value;
    }
}
