package net.vanfleteren.jazygit.git.model;

/**
 * How a branch is pushed.
 */
public enum PushMode {
    /**
     * Only when the remote branch is part of the local one.
     */
    FAST_FORWARD,
    /**
     * Also over a remote branch that has diverged, but only if it is still where this repository last
     * saw it ({@code --force-with-lease}), and the local branch once had it ({@code --force-if-includes}): not over
     * what someone else pushed, even once it was fetched (ADR 116).
     */
    FORCE_WITH_LEASE
}
