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
     * How tall pane {@code pane} of {@code panes} is, in percent of the height of their {@code column}, with its
     * bottom border at row {@code y}; none for a column without a height. It is the nearest percent, which the left
     * column rounds back to row {@code y} when it is at most 100 rows tall.
     */
    public static Option<Integer> height(Rect column, List<Rect> panes, int pane, int y) {
        return Option.when(pane >= 0 && pane < panes.size() && column.height() > 0, () -> panes.get(pane))
                .map(top -> Math.round((y + 1 - top.y()) * 100f / column.height()));
    }
}
