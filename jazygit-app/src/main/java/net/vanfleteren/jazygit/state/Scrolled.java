package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * Where an area of the content panel is scrolled to, and what it showed then: a position only holds for what it
 * was scrolled in, as other content starts at the top.
 *
 * @param content what the area showed
 * @param row     the first line shown
 * @param column  the first column shown
 * @param cursor  the stop the cursor of a rich diff is on (ADR 60); none for the first stop, and for what has no cursor
 * @param mark    the line the range of the cursor was started on with {@code v}, while one is open (ADR 60)
 */
public record Scrolled(ScrolledContent content, int row, int column, Option<Anchor> cursor, Option<Anchor> mark) {

    /**
     * {@code content} from its start.
     */
    public static Scrolled top(ScrolledContent content) {
        return new Scrolled(content, 0, 0, Option.none(), Option.none());
    }
}
