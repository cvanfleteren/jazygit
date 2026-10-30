package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Rect;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.Settings;
import org.junit.jupiter.api.Test;

class PaneBorderTest {

    // A column 40 rows tall, of which three panes under a status of 3 rows take rows 3 to 12, 13 to 32 and 33 to 39.
    private static final Rect COLUMN = new Rect(0, 0, 30, 40);
    private static final List<Rect> PANES =
            List.of(new Rect(0, 3, 30, 10), new Rect(0, 13, 30, 20), new Rect(0, 33, 30, 7));

    @Test
    void theBottomBorderOfEachPaneButTheLastIsABorder() {
        assertEquals(Option.of(0), PaneBorder.at(PANES, 5, 12));
        assertEquals(Option.of(1), PaneBorder.at(PANES, 29, 32));
        assertEquals(Option.none(), PaneBorder.at(PANES, 5, 39));
    }

    @Test
    void theTopBorderOfAPaneOrOutsideTheColumnIsNoBorder() {
        assertEquals(Option.none(), PaneBorder.at(PANES, 5, 13));
        assertEquals(Option.none(), PaneBorder.at(PANES, 5, 20));
        assertEquals(Option.none(), PaneBorder.at(PANES, 30, 12));
        assertEquals(Option.none(), PaneBorder.at(List.empty(), 5, 12));
    }

    @Test
    void aBottomBorderIsInPercentOfTheColumnBelowTheTopOfTheFirstPane() {
        assertEquals(Option.of(25), PaneBorder.bottom(COLUMN, PANES, 12));
        assertEquals(Option.of(75), PaneBorder.bottom(COLUMN, PANES, 32));
        // 11 rows are 27.5 percent.
        assertEquals(Option.of(28), PaneBorder.bottom(COLUMN, PANES, 13));
    }

    @Test
    void aColumnWithoutHeightOrWithoutPanesHasNone() {
        assertEquals(Option.none(), PaneBorder.bottom(new Rect(0, 0, 30, 0), PANES, 5));
        assertEquals(Option.none(), PaneBorder.bottom(COLUMN, List.empty(), 5));
    }

    @Test
    void draggingABorderMovesItToTheRowDraggedToAndNoOtherBorder() {
        List<Integer> heights = Settings.DEFAULT_REPOSITORY_HEIGHTS;
        List.rangeClosed(20, 100).forEach(height -> {
            Rect column = new Rect(0, 0, 30, height);
            List<Integer> rows = LeftColumn.paneRows(heights, height);
            List<Rect> panes = drawn(rows);
            List.range(0, heights.size())
                    .forEach(pane -> panes.forEach(dragTo -> {
                        int y = dragTo.bottom() - 1;
                        int bottom = PaneBorder.bottom(column, panes, y).get();
                        List<Integer> resized = Settings.resizedHeights(
                                heights, pane, bottom - heights.take(pane).sum().intValue());
                        List<Integer> borders = bottoms(drawn(LeftColumn.paneRows(resized, height)));
                        List<Integer> before = bottoms(panes);
                        String what = "pane " + pane + " to row " + y + " of " + height;
                        List.range(0, borders.size())
                                .filter(other -> other != pane)
                                .forEach(other -> assertEquals(before.get(other), borders.get(other), what));
                        if (resized.get(pane) - heights.get(pane)
                                == bottom - heights.take(pane + 1).sum().intValue()) {
                            assertEquals(y, (int) borders.get(pane), what);
                        }
                    }));
        });
    }

    /**
     * The panes under a status of 3 rows, {@code rows} tall.
     */
    private static List<Rect> drawn(List<Integer> rows) {
        List<Integer> tops = rows.scanLeft(3, Integer::sum);
        return rows.zipWith(tops, (tall, top) -> new Rect(0, top, 30, tall));
    }

    private static List<Integer> bottoms(List<Rect> panes) {
        return panes.map(pane -> pane.bottom() - 1);
    }
}
