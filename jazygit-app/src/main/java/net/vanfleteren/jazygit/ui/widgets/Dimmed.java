package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import net.vanfleteren.jazygit.ui.Theme.Backdrop;

/**
 * {@code content} rendered dimmed, as it is behind a popup: every cell is dimmed as {@code backdrop} says. It is
 * only drawn: its elements take no focus and no events, so Tab goes round the fields of the popup alone.
 */
public record Dimmed(Element content, Backdrop backdrop) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        content.render(frame, area, inert(context));
        Buffer buffer = frame.buffer();
        Rect dimmed = buffer.area().intersection(area);
        for (int y = dimmed.top(); y < dimmed.bottom(); y++) {
            for (int x = dimmed.left(); x < dimmed.right(); x++) {
                Cell cell = buffer.get(x, y);
                buffer.set(x, y, cell.style(backdrop.dim(cell.style())));
            }
        }
    }

    /**
     * A context that styles as {@code context} does, but keeps the elements rendered with it to itself.
     */
    private static RenderContext inert(RenderContext context) {
        if (!(context instanceof DefaultRenderContext rendering)) {
            return context;
        }
        FocusManager focus = new FocusManager();
        DefaultRenderContext inert = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        rendering.styleEngine().ifPresent(inert::setStyleEngine);
        inert.setBindings(rendering.bindings());
        inert.setFaultTolerant(rendering.isFaultTolerant());
        return inert;
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
