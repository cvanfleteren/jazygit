package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import io.vavr.collection.Vector;
import java.util.Optional;

/**
 * What a pane shows inside its border, with the scrollbar of its list on its right border instead of in a column of
 * its own, as the areas of the content panel show theirs. TamboUI's lists and trees keep where they are scrolled to to
 * themselves and draw their scrollbar in their last column, so the content is drawn a column wider, over the border,
 * which puts that column on it. The thumb, drawn in {@link #FOUND}, is drawn again as {@link #DOWNWARDS_THUMB} and
 * the border is put back around it. Content that draws no bar is drawn again as wide as it is, as the column it
 * would have had over the border would be cut off by it.
 */
public final class BorderBar implements Element {

    /** The thumb of a bar on a right border, which shows how far down what is inside is scrolled. */
    public static final String DOWNWARDS_THUMB = "┃";

    /** The thumb of a bar on a bottom border, which shows how far sideways what is inside is scrolled. */
    public static final String SIDEWAYS_THUMB = "━";

    /**
     * The colour lists are told to draw the thumb of their scrollbar in, by which it is found: it is never shown, and
     * no theme draws in it.
     */
    public static final Color FOUND = Color.rgb(1, 2, 3);

    /** How a thumb is drawn over the border: in the terminal's own colour. */
    public static final Style THUMB = Style.EMPTY.fg(Color.RESET);

    private final Element content;

    private BorderBar(Element content) {
        this.content = content;
    }

    /**
     * {@code content}, its scrollbar drawn on the right border of the pane it is drawn in.
     */
    public static Element of(Element content) {
        return new BorderBar(content);
    }

    @Override
    public void render(Frame frame, Rect at, RenderContext context) {
        Buffer buffer = frame.buffer();
        Rect wider = new Rect(at.x(), at.y(), at.width() + 1, at.height());
        // The border is the column right of the content, which is on the screen when the pane has a border.
        if (at.isEmpty() || !buffer.area().intersection(wider).equals(wider)) {
            context.renderChild(content, frame, at);
            return;
        }
        Vector<Cell> before = cells(buffer, wider);
        int border = at.right();
        context.renderChild(content, frame, wider);
        Vector<Integer> rows = Vector.range(at.top(), at.bottom());
        Vector<Integer> thumb =
                rows.filter(y -> buffer.get(border, y).style().fg().equals(Optional.of(FOUND)));
        if (thumb.isEmpty()) {
            before.forEachWithIndex((cell, index) ->
                    buffer.set(wider.left() + index % wider.width(), wider.top() + index / wider.width(), cell));
            context.renderChild(content, frame, at);
            return;
        }
        rows.forEach(y -> {
            Cell line = before.get((y - wider.top()) * wider.width() + wider.width() - 1);
            buffer.set(
                    border,
                    y,
                    thumb.contains(y) ? new Cell(DOWNWARDS_THUMB, line.style().patch(THUMB)) : line);
        });
    }

    private static Vector<Cell> cells(Buffer buffer, Rect area) {
        return Vector.range(area.top(), area.bottom())
                .flatMap(y -> Vector.range(area.left(), area.right()).map(x -> buffer.get(x, y)));
    }

    @Override
    public Constraint constraint() {
        return content.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return content.preferredSize(availableWidth, availableHeight, context);
    }
}
