package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Rect;
import io.vavr.collection.List;
import io.vavr.control.Option;
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
    void theHeightIsInPercentOfTheColumn() {
        assertEquals(Option.of(25), PaneBorder.height(COLUMN, PANES, 0, 12));
        assertEquals(Option.of(50), PaneBorder.height(COLUMN, PANES, 1, 32));
        // 11 rows are 27.5 percent.
        assertEquals(Option.of(28), PaneBorder.height(COLUMN, PANES, 0, 13));
    }

    @Test
    void theHeightRoundsBackToTheRowDraggedTo() {
        List.range(3, 40).forEach(y -> {
            int height = PaneBorder.height(COLUMN, PANES, 0, y).get();
            assertEquals(y + 1 - 3, LeftColumn.paneRows(List.of(height), 40).head(), "row " + y);
        });
    }

    @Test
    void aColumnWithoutHeightOrAPaneNotThereHasNone() {
        assertEquals(Option.none(), PaneBorder.height(new Rect(0, 0, 30, 0), PANES, 0, 5));
        assertEquals(Option.none(), PaneBorder.height(COLUMN, PANES, 3, 5));
    }
}
