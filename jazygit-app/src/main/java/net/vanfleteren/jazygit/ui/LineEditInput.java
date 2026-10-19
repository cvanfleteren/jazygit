package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextInputElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.ui.edit.LineEdit;

/**
 * A text input that also knows the {@link LineEdit readline keys}. The plain text input handles its keys before
 * any handler that can be attached to it, and takes Ctrl+Left as a plain Left; so this element registers itself
 * with the same id, ahead of the text input, to see the keys first. It hands the keys on to the text input itself,
 * so it can tell when the text changed. Handlers attached to the text input still get the keys neither takes.
 */
public final class LineEditInput extends StyledElement<LineEditInput> {

    private final TextInputElement input;
    private final TextInputState state;
    private Consumer<String> onChange = text -> {};

    /**
     * @param input the text input, with its size already set; it gets the id
     * @param state the state of the text input
     */
    public LineEditInput(TextInputElement input, TextInputState state, String id) {
        this.input = input.id(id);
        this.state = state;
        id(id);
        Option.of(input.constraint()).forEach(this::constraint);
    }

    /**
     * Calls {@code onChange} with the text whenever a key changed it.
     */
    public LineEditInput onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (!focused) {
            return EventResult.UNHANDLED;
        }
        String before = state.text();
        // Left unhandled, the text input itself, registered under the same id, is tried next, then its handlers.
        boolean handled = LineEdit.handle(state, event)
                || input.handleKeyEvent(event, true).isHandled();
        if (!state.text().equals(before)) {
            onChange.accept(state.text());
        }
        return handled ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return input.preferredSize(availableWidth, availableHeight, context);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (context instanceof DefaultRenderContext rendering) {
            // Registering is first come, first served; the text input would otherwise be ahead.
            rendering.registerElement(this, area);
        }
        input.render(frame, area, context);
    }
}
