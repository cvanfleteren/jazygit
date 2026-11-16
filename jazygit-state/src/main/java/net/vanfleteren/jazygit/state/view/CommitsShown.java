package net.vanfleteren.jazygit.state.view;

/**
 * Which commits the logs list.
 */
public enum CommitsShown {
    /**
     * All commits of the log.
     */
    ALL,
    /**
     * Only the commits not on the base of the branch, and the commit they forked from.
     */
    AHEAD;

    /**
     * Whether only the commits not on their base are listed.
     */
    public boolean ahead() {
        return this == AHEAD;
    }

    /**
     * The other choice.
     */
    public CommitsShown toggled() {
        return this == ALL ? AHEAD : ALL;
    }
}
