package net.vanfleteren.jazygit.gh.model;

/**
 * How a merge of a pull request, which GitHub does in the background, is doing.
 */
public sealed interface MergeStatus {

    /**
     * Not done yet.
     *
     * @param id what GitHub knows the merge by, to ask how it is doing
     */
    record Pending(String id) implements MergeStatus {}

    /**
     * Merged into its base branch, with the ones below it in its stack.
     */
    record Merged() implements MergeStatus {}

    /**
     * Added to the merge queue of its base branch, which merges it later.
     */
    record Enqueued() implements MergeStatus {}

    /**
     * Not merged.
     *
     * @param message why, as GitHub says it
     */
    record Failed(String message) implements MergeStatus {}
}
