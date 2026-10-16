package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import net.vanfleteren.jazygit.ui.Theme.Backdrop;

/**
 * {@code content} rendered dimmed, as it is behind a popup: every cell is dimmed as {@code backdrop} says.
 */
public record Dimmed(Element content, Backdrop backdrop) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        content.render(frame, area, context);
        Buffer buffer = frame.buffer();
        Rect dimmed = buffer.area().intersection(area);
        for (int y = dimmed.top(); y < dimmed.bottom(); y++) {
            for (int x = dimmed.left(); x < dimmed.right(); x++) {
                Cell cell = buffer.get(x, y);
                buffer.set(x, y, cell.style(backdrop.dim(cell.style())));
            }
        }
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
