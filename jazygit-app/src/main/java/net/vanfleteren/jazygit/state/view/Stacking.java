package net.vanfleteren.jazygit.state.view;

/**
 * How the branches pane lists the branches of a stack (ADR 87).
 */
public enum Stacking {
    /**
     * Under the header of their stack, which can be closed (ADR 72).
     */
    STACKED,
    /**
     * As any other branch, by when they last saw activity, with no header.
     */
    UNSTACKED;

    /**
     * The other way of listing them.
     */
    public Stacking toggled() {
        return this == STACKED ? UNSTACKED : STACKED;
    }
}
