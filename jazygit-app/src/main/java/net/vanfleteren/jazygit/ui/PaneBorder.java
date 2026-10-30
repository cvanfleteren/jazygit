package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Where the borders between the panes of the left column under the status are, as a frame drew them, and where
 * dragging one puts it.
 */
public final class PaneBorder {

    private PaneBorder() {}

    /**
     * The pane of {@code panes}, top to bottom, whose bottom border {@code (x, y)} is on, but the last, whose bottom
     * border is the bottom of the column. The top border of the pane under it is not one, as it holds the title, of
     * which some parts are clicked.
     */
    public static Option<Integer> at(List<Rect> panes, int x, int y) {
        return panes.dropRight(1)
                .zipWithIndex()
                .find(pane -> pane._1.contains(x, y) && y == pane._1.bottom() - 1)
                .map(pane -> pane._2);
    }

    /**
     * How far below the top of the first of {@code panes} a bottom border at row {@code y} is, in percent of the height
     * of their {@code column}; none for a column without a height or without panes. It is the nearest percent, which
     * the left column rounds back to row {@code y} when it is at most 100 rows tall.
     */
    public static Option<Integer> bottom(Rect column, List<Rect> panes, int y) {
        return panes.headOption()
                .filter(first -> column.height() > 0)
                .map(first -> Math.round((y + 1 - first.y()) * 100f / column.height()));
    }
}
