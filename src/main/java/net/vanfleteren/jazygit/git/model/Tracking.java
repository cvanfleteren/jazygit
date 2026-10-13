package net.vanfleteren.jazygit.git.model;

/**
 * What a local branch follows on its remote.
 */
public sealed interface Tracking {

    /**
     * It has no upstream: it was never pushed, or not with one set.
     */
    record None() implements Tracking {
    }

    /**
     * It follows {@code upstream}.
     */
    record Tracks(Upstream upstream) implements Tracking {
    }

    /**
     * Its upstream is configured, but the remote branch is deleted, e.g. after its pull request was merged.
     *
     * @param name the short name the upstream had, e.g. {@code origin/feature}
     */
    record Gone(String name) implements Tracking {
    }
}
