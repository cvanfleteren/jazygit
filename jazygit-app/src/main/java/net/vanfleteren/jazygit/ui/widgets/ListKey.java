package net.vanfleteren.jazygit.ui.widgets;

/**
 * What a key means to a list in a popup that can have a {@link ListFilter}, as {@link ListKeys#classify} tells it.
 * The list itself decides what that does.
 */
sealed interface ListKey {

    /**
     * A key that changes only the cursor of the list: it opens, edits or closes the filter, or moves the highlight.
     */
    sealed interface CursorKey extends ListKey {}

    /**
     * Escape while the filter is open: close it, showing all the rows again.
     */
    record CloseFilter() implements CursorKey {}

    /**
     * {@code /} while the filter is closed: open it, empty.
     */
    record OpenFilter() implements CursorKey {}

    /**
     * A key that edits the open filter.
     *
     * @param text the filter after the key
     */
    record EditFilter(String text) implements CursorKey {}

    /**
     * A key that moves the highlight.
     *
     * @param line where it moves to; the line it is on when it cannot move further
     */
    record Move(int line) implements CursorKey {}

    /**
     * Escape while the filter is closed.
     */
    record Cancel() implements ListKey {}

    /**
     * Enter, for the highlighted row.
     */
    record Confirm() implements ListKey {}
}
