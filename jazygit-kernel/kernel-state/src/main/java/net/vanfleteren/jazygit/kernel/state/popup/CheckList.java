package net.vanfleteren.jazygit.kernel.state.popup;

/**
 * The list of the checks on the tip of a branch in its popup, as every forge shows it (ADR 32, 160, 195): how many
 * are shown at once, and how far it scrolls to show the one picked. How far it is scrolled is the line of the
 * popup's list cursor: the first check shown (ADR 2).
 */
public final class CheckList {

    /**
     * The checks listed at once; the others are scrolled to.
     */
    public static final int VISIBLE = 16;

    private CheckList() {}

    /**
     * Where the list of {@code size} checks is scrolled to when the one at {@code to} is picked: as little from where
     * {@code cursor} has it as it takes to show it.
     */
    public static ListCursor scrolledTo(ListCursor cursor, int to, int size) {
        int last = Math.max(0, size - VISIBLE);
        int first = Math.clamp(cursor.line().getOrElse(0), 0, last);
        return cursor.at(Math.clamp(first, Math.max(0, to - VISIBLE + 1), Math.min(to, last)));
    }
}
