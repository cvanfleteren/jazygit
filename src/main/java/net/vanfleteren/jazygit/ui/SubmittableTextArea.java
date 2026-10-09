package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/**
 * A text area where Ctrl+Enter submits instead of inserting a newline. The plain text area turns
 * every Enter into a newline, whatever the modifiers, and handles its keys before any handler that
 * can be attached to it; so this element registers itself with the same id, ahead of the text area,
 * to see the keys first.
 */
final class SubmittableTextArea extends StyledElement<SubmittableTextArea> {

    private final TextAreaElement area;
    private final Runnable onSubmit;
    private Integer height;

    /**
     * @param area     the text area; it gets the id
     * @param onSubmit what Ctrl+Enter does
     */
    SubmittableTextArea(TextAreaElement area, String id, Runnable onSubmit) {
        this.area = area.id(id);
        this.onSubmit = onSubmit;
        id(id);
    }

    /**
     * Makes the text area this many rows high, borders included.
     */
    SubmittableTextArea rows(int rows) {
        this.height = rows;
        return length(rows);
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (focused && event.code() == KeyCode.ENTER && event.hasCtrl()) {
            onSubmit.run();
            return EventResult.HANDLED;
        }
        // The text area itself is registered under the same id, and is tried next.
        return EventResult.UNHANDLED;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        Size size = area.preferredSize(availableWidth, availableHeight, context);
        return height == null ? size : Size.of(size.widthOr(availableWidth), height);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (context instanceof DefaultRenderContext rendering) {
            // Registering is first come, first served; the text area would otherwise be ahead.
            rendering.registerElement(this, area);
        }
        this.area.render(frame, area, context);
    }
}
