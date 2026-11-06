package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.buffer.DiffResult;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.image.Picture;
import org.junit.jupiter.api.Test;

/**
 * Pictures painted with Sixel over the cells that say where they are (ADR 87).
 */
class SixelImagesTest {

    private static final Rect AREA = new Rect(0, 0, 20, 8);
    private static final Option<CellPixels> CELL = Option.of(new CellPixels(4, 8));
    private static final String DCS = "\u001bP0;1;0q";

    @Test
    void anImageIsPaintedOnceWhereItsCellsAreWhichAreBlank() {
        Picture picture = KittyGraphicsTest.picture(3, 4);
        Buffer frame = withPicture(picture, 2, 1, 0, 2);
        SixelImages images = new SixelImages();

        images.plan(frame, List.of(picture.sharp().get()), CELL);
        String painted = images.paint().get();

        assertTrue(painted.startsWith("\u001b7\u001b[2;3H" + DCS + "\"1;1;12;16#"), painted);
        assertTrue(painted.contains("\u001b\\"), painted);
        assertTrue(painted.endsWith("\u001b[0m\u001b8"), painted);
        assertEquals(Cell.EMPTY, frame.get(2, 1));
        assertEquals(Cell.EMPTY, frame.get(4, 2));
        images.plan(withPicture(picture, 2, 1, 0, 2), List.of(picture.sharp().get()), CELL);
        assertEquals(Option.none(), images.paint());
    }

    @Test
    void onlyThePartOfAnImageThatShowsIsPainted() {
        Picture picture = KittyGraphicsTest.picture(3, 6);
        // Its first row is scrolled out of the pane, above the screen.
        Buffer frame = withPicture(picture, 0, -1, 1, 3);
        SixelImages images = new SixelImages();

        images.plan(frame, List.of(picture.sharp().get()), CELL);

        assertTrue(images.paint().get().startsWith("\u001b7\u001b[1;1H" + DCS + "\"1;1;12;16#"));
    }

    @Test
    void whatIsOverAnImageIsWrittenAgainAfterIt() {
        Picture picture = KittyGraphicsTest.picture(3, 4);
        Buffer frame = withPicture(picture, 0, 0, 0, 2);
        frame.set(1, 1, new Cell("x", Style.EMPTY));
        SixelImages images = new SixelImages();

        images.plan(frame, List.of(picture.sharp().get()), CELL);
        String painted = images.paint().get();

        assertTrue(painted.indexOf("\u001b[2;2H") > painted.indexOf("\u001b\\"), painted);
        assertTrue(painted.substring(painted.indexOf("\u001b[2;2H")).contains("x"), painted);
    }

    @Test
    void anImageIsPaintedAgainWhenACellOfItIsWritten() {
        Picture picture = KittyGraphicsTest.picture(3, 4);
        SixelImages images = new SixelImages();
        images.plan(withPicture(picture, 0, 0, 0, 2), List.of(picture.sharp().get()), CELL);
        images.paint();
        Buffer before = Buffer.empty(AREA);
        Buffer after = Buffer.empty(AREA);
        after.set(10, 5, new Cell("y", Style.EMPTY));
        DiffResult outside = new DiffResult();
        before.diff(after, outside);

        images.drawn(outside);
        assertEquals(Option.none(), images.paint());

        after.set(1, 1, new Cell("z", Style.EMPTY));
        DiffResult inside = new DiffResult();
        before.diff(after, inside);
        images.drawn(inside);
        assertTrue(images.paint().isDefined());
    }

    @Test
    void whereAnImageWasItsCellsAreWrittenAgain() {
        Picture picture = KittyGraphicsTest.picture(3, 4);
        SixelImages images = new SixelImages();
        images.plan(withPicture(picture, 0, 0, 0, 2), List.of(picture.sharp().get()), CELL);
        images.paint();
        Buffer gone = Buffer.empty(AREA);
        gone.setString(0, 0, "abc", Style.EMPTY);

        images.plan(gone, List.of(picture.sharp().get()), CELL);
        String painted = images.paint().get();

        assertFalse(painted.contains(DCS));
        assertTrue(painted.contains("\u001b[1;1H") && painted.contains("abc"), painted);
    }

    @Test
    void withoutTheSizeOfACellAPictureIsHalfBlocks() {
        Picture picture = KittyGraphicsTest.picture(3, 4);
        Buffer frame = withPicture(picture, 0, 0, 0, 2);
        SixelImages images = new SixelImages();

        images.plan(frame, List.of(picture.sharp().get()), Option.none());

        assertEquals("▀", frame.get(0, 0).symbol());
        assertEquals(Option.none(), images.paint());
    }

    @Test
    void theCellsOfAnImageNotShownAreBlank() {
        Picture picture = KittyGraphicsTest.picture(3, 4);
        Buffer frame = withPicture(picture, 0, 0, 0, 2);

        new SixelImages().plan(frame, List.empty(), CELL);

        assertEquals(Cell.EMPTY, frame.get(0, 0));
    }

    /**
     * A screen with the rows {@code from} up to {@code to} of {@code picture}'s cells, its first cell at {@code x} and
     * {@code y}; rows off the screen are left out.
     */
    private static Buffer withPicture(Picture picture, int x, int y, int from, int to) {
        Buffer frame = Buffer.empty(AREA);
        for (int row = from; row < to; row++) {
            for (int column = 0; column < picture.width(); column++) {
                Tuple2<Style, String> cell = KittyGraphics.cell(picture, row * picture.width() + column);
                if (y + row >= 0) {
                    frame.set(x + column, y + row, new Cell(cell._2(), cell._1()));
                }
            }
        }
        return frame;
    }
}
