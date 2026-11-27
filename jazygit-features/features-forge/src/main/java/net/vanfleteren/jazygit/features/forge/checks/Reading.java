package net.vanfleteren.jazygit.features.forge.checks;

/**
 * Whether the checks shown are read again, and why (ADR 34).
 */
public enum Reading {
    /** They are not. */
    NOT,
    /** By themselves, as they are due: no spinner. */
    BY_ITSELF,
    /** As the user asked: with a spinner. */
    ASKED
}
