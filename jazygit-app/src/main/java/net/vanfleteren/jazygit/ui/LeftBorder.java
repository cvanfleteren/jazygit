package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import io.vavr.control.Option;

/**
 * Where the border between the left column and the content panel is, as a frame drew them, and where dragging it
 * puts it.
 */
public final class LeftBorder {

    private LeftBorder() {}

    /**
     * Whether {@code (x, y)} is on the border of the left column {@code left} of {@code panes}: the right edge of
     * the left column, or the left edge of the content panel next to it.
     */
    public static boolean contains(Rect panes, Rect left, int x, int y) {
        return panes.contains(x, y) && (x == left.right() - 1 || x == left.right());
    }

    /**
     * How wide the left column of {@code panes} is, in percent of their width, with its right edge at column
     * {@code x}; none for panes without a width. It is the nearest percent, which the layout rounds back to column
     * {@code x} when the panes are less than 100 columns wide.
     */
    public static Option<Integer> width(Rect panes, int x) {
        return Option.when(panes.width() > 0, () -> Math.round((x + 1 - panes.x()) * 100f / panes.width()));
    }
}
