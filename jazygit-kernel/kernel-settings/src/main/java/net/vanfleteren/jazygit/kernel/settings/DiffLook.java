package net.vanfleteren.jazygit.kernel.settings;

/**
 * How a diff is drawn: in which style, whether the rich view colours its code by its syntax (ADR 53), whether it
 * highlights the words that changed in a changed line, or only the line, and whether the unified rich view shows the
 * changed lines in pairs (ADR 83), or as git gives them.
 */
public record DiffLook(DiffStyle style, boolean syntax, boolean words, boolean paired) {}
