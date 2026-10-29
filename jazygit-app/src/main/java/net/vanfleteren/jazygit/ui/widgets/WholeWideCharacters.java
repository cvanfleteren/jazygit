package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;

/**
 * {@code content} with no half of a wide character left: a popup drawn over half of an emoji leaves the other half
 * standing, and blank instead. A first half left standing is drawn whole by the terminal, over the border of the
 * popup, and every cell drawn after it on that row lands a column further right.
 */
public record WholeWideCharacters(Element content) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        content.render(frame, area, context);
        Buffer buffer = frame.buffer();
        Rect checked = buffer.area().intersection(area);
        for (int y = checked.top(); y < checked.bottom(); y++) {
            for (int x = checked.left(); x < checked.right(); x++) {
                Cell cell = buffer.get(x, y);
                boolean firstHalfAlone = !cell.isContinuation()
                        && CharWidth.of(cell.symbol()) == 2
                        && (x + 1 >= checked.right() || !buffer.get(x + 1, y).isContinuation());
                boolean secondHalfAlone =
                        cell.isContinuation() && (x == checked.left() || !isFirstHalf(buffer.get(x - 1, y)));
                if (firstHalfAlone || secondHalfAlone) {
                    buffer.set(x, y, cell.symbol(" "));
                }
            }
        }
    }

    private static boolean isFirstHalf(Cell cell) {
        return !cell.isContinuation() && CharWidth.of(cell.symbol()) == 2;
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
