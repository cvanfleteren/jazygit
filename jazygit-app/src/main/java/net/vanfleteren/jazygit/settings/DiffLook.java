package net.vanfleteren.jazygit.settings;

/**
 * How a diff is drawn: in which style, and whether the rich view colours its code by its syntax (ADR 53).
 */
public record DiffLook(DiffStyle style, boolean syntax) {

    /**
     * Git's text.
     */
    public static final DiffLook PLAIN = new DiffLook(DiffStyle.PLAIN, true);
    /**
     * The rich view, its code coloured by its syntax.
     */
    public static final DiffLook RICH = new DiffLook(DiffStyle.RICH, true);
}
