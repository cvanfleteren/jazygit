package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import java.util.function.Function;

/**
 * {@code element}, whose keys go to {@code keys} first, and only those it leaves unhandled to the element. Unlike a
 * handler set with {@code onKeyEvent}, {@code keys} can move the focus on, with {@link EventResult#FOCUS_NEXT} or
 * {@link EventResult#FOCUS_PREVIOUS}: a list runs its {@code onKeyEvent} handler itself and drops those. This element
 * registers itself under the id of the element, ahead of it, so the router gives it the keys of that id.
 */
public final class KeysFirst extends StyledElement<KeysFirst> {

    private final Element element;
    private final Function<KeyEvent, EventResult> keys;

    /**
     * @param element the element, or what draws it, which takes the focus under {@code id}
     * @param id      the id of the element that takes the focus
     * @param keys    what the keys do while it has the focus
     */
    public KeysFirst(Element element, String id, Function<KeyEvent, EventResult> keys) {
        this.element = element;
        this.keys = keys;
        id(id);
        constraint(element.constraint());
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (!focused) {
            return EventResult.UNHANDLED;
        }
        EventResult result = keys.apply(event);
        return result == EventResult.UNHANDLED ? element.handleKeyEvent(event, true) : result;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return element.preferredSize(availableWidth, availableHeight, context);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (context instanceof DefaultRenderContext rendering) {
            // Registering is first come, first served; the element would otherwise be ahead.
            rendering.registerElement(this, area);
        }
        element.render(frame, area, context);
    }
}
