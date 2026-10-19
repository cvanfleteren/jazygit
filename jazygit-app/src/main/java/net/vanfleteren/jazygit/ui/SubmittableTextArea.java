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
import dev.tamboui.widgets.input.TextAreaState;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.ui.edit.LineEdit;
import net.vanfleteren.jazygit.ui.widgets.BottomTitle;

/**
 * A text area where Ctrl+Enter submits instead of inserting a newline, where the terminal tells it from Enter; most
 * send both alike. The plain text area turns
 * every Enter into a newline, whatever the modifiers, and handles its keys before any handler that
 * can be attached to it; so this element registers itself with the same id, ahead of the text area,
 * to see the keys first. It also knows the {@link LineEdit readline keys}, which the text area would take as
 * plain ones. It hands the other keys on to the text area itself, so it can tell when the text changed.
 */
public final class SubmittableTextArea extends StyledElement<SubmittableTextArea> {

    private final TextAreaElement area;
    private final TextAreaState state;
    private final Runnable onSubmit;
    private Consumer<String> onChange = text -> {};
    // The rows the text area takes, borders included, if it is told.
    private Option<Integer> height = Option.none();
    // Shown in the bottom border, if any.
    private Option<String> bottomTitle = Option.none();

    /**
     * @param area     the text area; it gets the id
     * @param state    the state of the text area
     * @param onSubmit what Ctrl+Enter does
     */
    public SubmittableTextArea(TextAreaElement area, TextAreaState state, String id, Runnable onSubmit) {
        this.area = area.id(id);
        this.state = state;
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

    /**
     * Calls {@code onChange} with the text whenever a key changed it.
     */
    public SubmittableTextArea onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (!focused) {
            return EventResult.UNHANDLED;
        }
        if (event.code() == KeyCode.ENTER && event.hasCtrl()) {
            onSubmit.run();
            return EventResult.HANDLED;
        }
        String before = state.text();
        // Left unhandled, the text area itself, registered under the same id, is tried next, then its handlers.
        boolean handled = LineEdit.handle(state, event)
                || area.handleKeyEvent(event, true).isHandled();
        if (!state.text().equals(before)) {
            onChange.accept(state.text());
        }
        return handled ? EventResult.HANDLED : EventResult.UNHANDLED;
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
        bottomTitle.forEach(title -> BottomTitle.draw(frame, area, title));
    }
}
