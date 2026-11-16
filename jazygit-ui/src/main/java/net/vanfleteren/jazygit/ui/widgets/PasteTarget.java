package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.PasteEvent;
import java.util.function.Consumer;

/**
 * {@code element}, drawn as it is, which takes what is pasted while the element of {@code id} in it has the focus: for
 * a line typed in that the keys of another element edit, like the filter of a list. TamboUI hands a paste to each
 * element under the focused id until one takes it, but the keys only to the first one, and a click to the last one
 * under it that takes the focus or the click. This element registers itself under the id after what it draws, as each
 * element does, takes no focus and no clicks: the keys and the mouse still go to the element of the id.
 */
public final class PasteTarget extends StyledElement<PasteTarget> {

    private final Element element;
    private final Consumer<PasteEvent> paste;

    /**
     * @param element what is drawn, with the element of {@code id} in it
     * @param id      the id of the element that has the focus while the line is typed in
     * @param paste   what a paste does
     */
    public PasteTarget(Element element, String id, Consumer<PasteEvent> paste) {
        this.element = element;
        this.paste = paste;
        id(id);
        constraint(element.constraint());
    }

    @Override
    public EventResult handlePasteEvent(PasteEvent event) {
        paste.accept(event);
        return EventResult.HANDLED;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return element.preferredSize(availableWidth, availableHeight, context);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        element.render(frame, area, context);
    }
}
