package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import lombok.With;

/**
 * Where the user is in the list of the open popup: the line that is highlighted, and the filter typed above it.
 *
 * @param line   the highlighted line among those shown; none until the user moves, so the popup highlights the line
 *               it opens at
 * @param filter the text of the filter, while it is open
 */
@With
public record ListCursor(Option<Integer> line, Option<String> filter) {

    /**
     * Where a list starts: at the line its popup opens at, without a filter.
     */
    public static final ListCursor START = new ListCursor(Option.none(), Option.none());

    /**
     * This cursor on {@code line}.
     */
    public ListCursor at(int line) {
        return withLine(Option.of(line));
    }
}
