package net.vanfleteren.jazygit.settings;

/**
 * How a diff is drawn: in which style, whether the rich view colours its code by its syntax (ADR 53), and whether it
 * highlights the words that changed in a changed line, or only the line.
 */
public record DiffLook(DiffStyle style, boolean syntax, boolean words) {}
