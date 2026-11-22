package net.vanfleteren.jazygit.core.forgejo;

/**
 * How a pull request that Forgejo merged went (ADR 180).
 */
public sealed interface ForgejoMerged {

    /**
     * Merged, and its head branch deleted when that was asked for.
     */
    record Merged() implements ForgejoMerged {}

    /**
     * Merged, but its head branch, which was to be deleted, was kept, for the reason {@code why} Forgejo gave: it is
     * the default branch, it is protected, or the user may not delete it. Forgejo answers that with a 403, after
     * merging.
     */
    record BranchKept(String why) implements ForgejoMerged {}
}
