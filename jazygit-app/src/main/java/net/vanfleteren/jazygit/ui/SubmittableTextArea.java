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
import io.vavr.control.Option;

/**
 * A text area where Ctrl+Enter submits instead of inserting a newline, where the terminal tells it from Enter; most
 * send both alike. The plain text area turns
 * every Enter into a newline, whatever the modifiers, and handles its keys before any handler that
 * can be attached to it; so this element registers itself with the same id, ahead of the text area,
 * to see the keys first.
 */
public final class SubmittableTextArea extends StyledElement<SubmittableTextArea> {

    private final TextAreaElement area;
    private final Runnable onSubmit;
    // The rows the text area takes, borders included, if it is told.
    private Option<Integer> height = Option.none();
    // Shown in the bottom border, if any.
    private Option<String> bottomTitle = Option.none();

    /**
     * @param area     the text area; it gets the id
     * @param onSubmit what Ctrl+Enter does
     */
    public SubmittableTextArea(TextAreaElement area, String id, Runnable onSubmit) {
        this.area = area.id(id);
        this.onSubmit = onSubmit;
        id(id);
    }

    /**
     * Makes the text area this many rows high, borders included.
     */
    public SubmittableTextArea rows(int rows) {
        this.height = Option.of(rows);
        return length(rows);
    }

    /**
     * Shows {@code title} in the bottom border, on the right, as the title is shown in the top one.
     */
    public SubmittableTextArea bottomTitle(String title) {
        this.bottomTitle = Option.of(title);
        return this;
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
        return height.map(rows -> Size.of(size.widthOr(availableWidth), rows)).getOrElse(size);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (context instanceof DefaultRenderContext rendering) {
            // Registering is first come, first served; the text area would otherwise be ahead.
            rendering.registerElement(this, area);
        }
        this.area.render(frame, area, context);
        bottomTitle.forEach(title -> drawBottomTitle(frame, area, title));
    }

    /**
     * Draws {@code title} on the bottom border of {@code area}, styled as the title in its top border, which the
     * text area drew just now; a title that does not fit is left out.
     */
    private static void drawBottomTitle(Frame frame, Rect area, String title) {
        int x = area.right() - 1 - title.length();
        int y = area.bottom() - 1;
        if (area.height() >= 2 && x > area.x()) {
            frame.buffer()
                    .setString(
                            x,
                            y,
                            title,
                            frame.buffer().get(area.x() + 1, area.y()).style());
        }
    }
}
