package net.vanfleteren.jazygit.kernel.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;

/**
 * {@code content} with {@code line} on the last row of where it is drawn, however few rows {@code content} needs: a
 * line at the bottom of a pane, below its list. Where there is no room for both, only {@code content} is drawn.
 */
final class LineBelow implements Element {

    private final Element content;
    private final Element line;

    LineBelow(Element content, Element line) {
        this.content = content;
        this.line = line;
    }

    @Override
    public void render(Frame frame, Rect at, RenderContext context) {
        if (at.height() < 2) {
            context.renderChild(content, frame, at);
            return;
        }
        context.renderChild(content, frame, new Rect(at.x(), at.y(), at.width(), at.height() - 1));
        context.renderChild(line, frame, new Rect(at.x(), at.bottom() - 1, at.width(), 1));
    }

    @Override
    public Constraint constraint() {
        return content.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        Size inside = content.preferredSize(availableWidth, availableHeight, context);
        return inside.hasKnownHeight() ? new Size(inside.width(), inside.height() + 1) : inside;
    }
}
