package net.vanfleteren.jazygit.git.model;

/**
 * What a local branch follows.
 */
public sealed interface Tracking {

    /**
     * It has no upstream: it was never pushed, or not with one set.
     */
    record None() implements Tracking {}

    /**
     * It follows {@code upstream}.
     */
    record Tracks(Upstream upstream) implements Tracking {}

    /**
     * Its upstream is configured, but the branch is deleted, e.g. on the remote after its pull request was merged.
     *
     * @param branch the branch that it followed, e.g. {@code origin/feature}
     */
    record Gone(BranchRef branch) implements Tracking {}
}
