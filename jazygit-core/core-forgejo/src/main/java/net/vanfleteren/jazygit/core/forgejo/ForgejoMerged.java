package net.vanfleteren.jazygit.core.forgejo;

/**
 * How a pull request that Forgejo was asked to merge went (ADR 180, 181).
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

    /**
     * Not merged yet, but scheduled to be once the rules of its base branch are met (ADR 181), as Forgejo answers a
     * merge when its checks succeed: with 201, also when they are met already.
     */
    record Scheduled() implements ForgejoMerged {}
}
