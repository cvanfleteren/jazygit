package net.vanfleteren.jazygit.kernel.state.diff;

/**
 * Which file of a diff a line is numbered in: the old one or the new one (ADR 171).
 */
public enum DiffSide {
    /**
     * The old file, which a removed line is of.
     */
    OLD,
    /**
     * The new file, which an added or a context line is of.
     */
    NEW
}
