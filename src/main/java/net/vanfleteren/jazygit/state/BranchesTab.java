package net.vanfleteren.jazygit.state;

/**
 * Which list the branches pane shows, in the order {@code [} and {@code ]} go through them.
 */
public enum BranchesTab {
    /** The local branches. */
    LOCAL,
    /** The remote-tracking branches. */
    REMOTE,
    /** The worktrees of the repository. */
    WORKTREES;

    /**
     * The tab {@code by} tabs after this one, going round: -1 is the previous one, 1 the next one.
     */
    public BranchesTab shifted(int by) {
        int count = values().length;
        return values()[Math.floorMod(ordinal() + by, count)];
    }
}
