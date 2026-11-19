package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import java.util.Optional;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * Renders an element in the dark theme with a given element focused, and tells the colour of the border around a
 * text on the screen, to see which field the themes border as having the focus.
 */
public final class ThemedBorders {

    private final Buffer buffer;

    private ThemedBorders(Buffer buffer) {
        this.buffer = buffer;
    }

    /**
     * {@code view} rendered in {@code area} in the dark theme, while {@code focused} has the focus.
     */
    public static ThemedBorders render(Element view, Rect area, String focused) {
        FocusManager focus = new FocusManager();
        focus.setFocus(focused);
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        context.setStyleEngine(Theme.createEngine().get());
        Buffer buffer = Buffer.empty(area);
        view.render(Frame.forTesting(buffer), area, context);
        return new ThemedBorders(buffer);
    }

    /**
     * The colour of the border left of {@code text}: the first cell on its line, going left, that is not blank. For
     * the title of a panel, that is its corner; for a line in it, its side.
     */
    public Optional<Color> borderLeftOf(String text) {
        return leftOf(text).style().fg();
    }

    private Cell leftOf(String text) {
        Rect area = buffer.area();
        for (int y = 0; y < area.height(); y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < area.width(); x++) {
                line.append(buffer.get(x, y).symbol());
            }
            int at = line.indexOf(text);
            for (int x = at - 1; at > 0 && x >= 0; x--) {
                if (!buffer.get(x, y).symbol().isBlank()) {
                    return buffer.get(x, y);
                }
            }
        }
        throw new AssertionError(text + " not shown with a border left of it");
    }
}
