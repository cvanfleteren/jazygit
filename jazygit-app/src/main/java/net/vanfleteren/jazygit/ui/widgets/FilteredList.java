package net.vanfleteren.jazygit.ui.widgets;

import net.vanfleteren.jazygit.state.ListCursor;

/**
 * What a list in a popup shows, as far as its keys need to know: its cursor in the model, the highlighted line, and
 * which of its lines can be highlighted.
 */
interface FilteredList {

    ListCursor cursor();

    /**
     * The highlighted line among those shown.
     */
    int selected();

    /**
     * How many lines are shown.
     */
    int size();

    /**
     * Whether {@code line} can be highlighted, unlike a heading.
     */
    boolean highlightable(int line);
}
