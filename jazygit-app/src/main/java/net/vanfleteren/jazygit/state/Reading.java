package net.vanfleteren.jazygit.state;

/**
 * Whether a dialog reads what it shows again, while that stays shown, and why: only a read the user asked for shows a
 * spinner in its title (ADR 34).
 */
public enum Reading {
    /**
     * It does not.
     */
    NOT,
    /**
     * It does by itself, as it does every so often.
     */
    BY_ITSELF,
    /**
     * It does by itself, but what it brings is dropped: it started before a change that it would show undone.
     */
    DROPPED,
    /**
     * It does as the user asked, with {@code R}.
     */
    ASKED
}
