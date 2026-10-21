package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class LeftBorderTest {

    // Panes from column 2, 50 wide, under a row; the left column is their first 15 columns.
    private static final Rect PANES = new Rect(2, 1, 50, 20);
    private static final Rect LEFT = new Rect(2, 1, 15, 20);

    @Test
    void theBorderIsTheRightEdgeOfTheLeftColumnAndTheLeftEdgeOfTheContentPanel() {
        assertTrue(LeftBorder.contains(PANES, LEFT, 16, 5));
        assertTrue(LeftBorder.contains(PANES, LEFT, 17, 5));
        assertFalse(LeftBorder.contains(PANES, LEFT, 15, 5));
        assertFalse(LeftBorder.contains(PANES, LEFT, 18, 5));
    }

    @Test
    void theBorderIsOnlyInTheRowsOfThePanes() {
        assertFalse(LeftBorder.contains(PANES, LEFT, 16, 0));
        assertFalse(LeftBorder.contains(PANES, LEFT, 16, 21));
    }

    @Test
    void theWidthCountsFromTheLeftOfThePanes() {
        // The right edge at column 16 makes the left column 15 of the 50 columns wide.
        assertEquals(Option.of(30), LeftBorder.width(PANES, 16));
    }

    @Test
    void leftOfThePanesTheWidthIsBelowZeroForTheUpdateToClamp() {
        assertEquals(Option.of(-2), LeftBorder.width(PANES, 0));
    }

    @Test
    void panesWithoutAWidthHaveNoWidth() {
        assertEquals(Option.none(), LeftBorder.width(new Rect(0, 0, 0, 20), 5));
    }

    @Test
    void theLayoutPutsTheBorderBackWhereTheMouseIsInPanesNarrowerThan100Columns() {
        for (int panesWidth : new int[] {37, 80, 99}) {
            Rect panes = new Rect(3, 0, panesWidth, 10);
            for (int x = panes.x() + 1; x < panes.right() - 1; x++) {
                int width = LeftBorder.width(panes, x).get();
                Rect left = Layout.horizontal()
                        .constraints(Constraint.percentage(width), Constraint.fill())
                        .split(panes)
                        .get(0);
                assertEquals(x, left.right() - 1, "panes " + panesWidth + " wide, mouse at " + x);
            }
        }
    }
}
