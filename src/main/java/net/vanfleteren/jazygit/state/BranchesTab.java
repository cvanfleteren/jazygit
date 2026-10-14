package net.vanfleteren.jazygit.state;

/**
 * Which branches the branches pane lists.
 */
public enum BranchesTab {
    /** The local branches. */
    LOCAL,
    /** The remote-tracking branches. */
    REMOTE;

    /**
     * The other tab; with two, the next and the previous one are the same.
     */
    public BranchesTab other() {
        return this == LOCAL ? REMOTE : LOCAL;
    }
}
