package net.vanfleteren.jazygit.state.diff;

/**
 * The match of the search of the content panel an area went to with {@code n}, {@code N} or Enter (ADR 126), by what
 * finds it again in another drawing of what the area shows: the rows of a rich diff move when it is styled again, as
 * its cursor's stop does not.
 */
public sealed interface Found {

    /**
     * The {@code nth} match, from 0, in the rows of the stop {@code stop} of a rich diff (ADR 60).
     */
    record InStop(Anchor stop, int nth) implements Found {}

    /**
     * The {@code nth} match, from 0, on line {@code line} of what has no cursor.
     */
    record OnLine(int line, int nth) implements Found {}
}
