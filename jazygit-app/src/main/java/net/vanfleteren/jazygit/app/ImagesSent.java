package net.vanfleteren.jazygit.app;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import java.util.function.Consumer;

/**
 * {@code content}, after which {@code images} is given the frame, to see which images its cells are of: to send them
 * with the Kitty graphics protocol, or to paint them with Sixel once the frame is written (ADR 188). It is to be the
 * whole screen, so that a cell covered by a popup or changed after it is drawn is as it is shown.
 */
record ImagesSent(Element content, Consumer<Frame> images) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        content.render(frame, area, context);
        images.accept(frame);
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
