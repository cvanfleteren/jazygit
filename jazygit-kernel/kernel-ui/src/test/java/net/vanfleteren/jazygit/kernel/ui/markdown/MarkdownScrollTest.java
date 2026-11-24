package net.vanfleteren.jazygit.kernel.ui.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.RenderContext;
import io.vavr.collection.HashMap;
import io.vavr.collection.Iterator;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Optional;
import net.vanfleteren.jazygit.lib.image.Picture;
import org.junit.jupiter.api.Test;

/**
 * Markdown that scrolls, with the pictures of its images drawn in it (ADR 190).
 */
class MarkdownScrollTest {

    private static final String URL = "https://github.com/user-attachments/assets/5a77";
    private static final int RED = 0xffff0000;
    private static final int GREEN = 0xff00ff00;
    private static final int BLUE = 0xff0000ff;
    private static final int WHITE = 0xffffffff;
    // Two pixels wide and four high: two cells wide and two rows high, red over green, then blue over white.
    private static final Picture SHOT = new Picture(2, 4, Vector.of(RED, RED, GREEN, GREEN, BLUE, BLUE, WHITE, WHITE));
    private static final String MARKDOWN = "Above\n\n<img alt=\"image\" src=\"" + URL + "\" />\n\nBelow";
    private static final Color PANE = Color.rgb(10, 20, 30);

    private final MarkdownScroll scroll = new MarkdownScroll();

    @Test
    void anImageOfWhichThereIsAPictureIsDrawnInHalfBlocks() {
        Buffer buffer = rendered(MARKDOWN, HashMap.of(URL, SHOT), 20, 10);

        int top = rowWith(buffer, "▀").get();
        assertEquals(Optional.of(colour(RED)), buffer.get(0, top).style().fg());
        assertEquals(Optional.of(colour(GREEN)), buffer.get(0, top).style().bg());
        assertEquals(Optional.of(colour(BLUE)), buffer.get(1, top + 1).style().fg());
        assertEquals(Optional.of(colour(WHITE)), buffer.get(1, top + 1).style().bg());
        assertFalse(text(buffer).contains("[image]"), text(buffer));
        assertTrue(text(buffer).contains("Above"), text(buffer));
        assertTrue(text(buffer).contains("Below"), text(buffer));
    }

    @Test
    void aPictureScrolledHalfOutOfViewShowsItsRowsInView() {
        rendered(MARKDOWN, HashMap.of(URL, SHOT), 20, 2);
        // Above, a blank line and the first row of the picture scrolled out of view.
        scroll.scrollBy(3);

        Buffer buffer = rendered(MARKDOWN, HashMap.of(URL, SHOT), 20, 2);

        assertEquals("▀", buffer.get(0, 0).symbol());
        assertEquals(Optional.of(colour(BLUE)), buffer.get(0, 0).style().fg());
        assertEquals(Optional.of(colour(WHITE)), buffer.get(0, 0).style().bg());
    }

    @Test
    void whereThePictureIsSeeThroughThePaneShows() {
        Picture clear = new Picture(1, 2, Vector.of(0, 0));

        Buffer buffer = rendered("<img src=\"" + URL + "\" width=\"8\" />\n\nBelow", HashMap.of(URL, clear), 20, 4);

        assertEquals(" ", buffer.get(0, 0).symbol());
        assertEquals(Optional.of(PANE), buffer.get(0, 0).style().bg());
    }

    @Test
    void anImageWithoutAPictureStaysThePlaceholder() {
        Buffer buffer = rendered(MARKDOWN, HashMap.empty(), 20, 10);

        assertTrue(text(buffer).contains("[image]"), text(buffer));
        assertEquals(Option.none(), rowWith(buffer, "▀"));
    }

    /**
     * {@code markdown} rendered by the scroll into an area {@code width} by {@code height}, on a pane of colour
     * {@link #PANE}.
     */
    private Buffer rendered(String markdown, Map<String, Picture> pictures, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        buffer.setStyle(area, Style.EMPTY.bg(PANE));
        scroll.render(markdown, pictures).render(Frame.forTesting(buffer), area, RenderContext.empty());
        return buffer;
    }

    private static Option<Integer> rowWith(Buffer buffer, String symbol) {
        Rect area = buffer.area();
        return Iterator.range(area.top(), area.bottom())
                .find(y -> Iterator.range(area.left(), area.right())
                        .map(x -> buffer.get(x, y))
                        .map(Cell::symbol)
                        .contains(symbol));
    }

    private static String text(Buffer buffer) {
        Rect area = buffer.area();
        return Iterator.range(area.top(), area.bottom())
                .map(y -> Iterator.range(area.left(), area.right())
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString())
                .mkString("\n");
    }

    private static Color colour(int argb) {
        return Color.rgb(argb >> 16 & 0xff, argb >> 8 & 0xff, argb & 0xff);
    }
}
