package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import net.vanfleteren.jazygit.ui.Theme.SelectedText;

/**
 * {@code content} with the plain text on its highlighted rows coloured as {@code selectedText} says.
 */
public record SelectedTextColoured(Element content, SelectedText selectedText) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        content.render(frame, area, context);
        if (selectedText.fg().isEmpty()) {
            return;
        }
        Buffer buffer = frame.buffer();
        Rect coloured = buffer.area().intersection(area);
        for (int y = coloured.top(); y < coloured.bottom(); y++) {
            for (int x = coloured.left(); x < coloured.right(); x++) {
                Cell cell = buffer.get(x, y);
                buffer.set(x, y, cell.style(selectedText.of(cell.style())));
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
