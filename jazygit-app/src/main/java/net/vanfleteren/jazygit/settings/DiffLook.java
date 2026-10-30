package net.vanfleteren.jazygit.settings;

/**
 * How a diff is drawn: in which style, and whether the rich view colours its code by its syntax (ADR 53).
 */
public record DiffLook(DiffStyle style, boolean syntax) {}
