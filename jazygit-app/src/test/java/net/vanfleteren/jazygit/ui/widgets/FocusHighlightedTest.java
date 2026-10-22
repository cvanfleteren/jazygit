package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import org.junit.jupiter.api.Test;

/**
 * A list in a popup, whose highlight shows as in use only while it has the focus.
 */
class FocusHighlightedTest {

    private static final Rect AREA = new Rect(0, 0, 20, 3);

    /**
     * The list of two rows, the first selected, rendered while {@code focused} has the focus.
     */
    private static Buffer rendered(String focused) {
        FocusManager focus = new FocusManager();
        focus.setFocus(focused);
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        Buffer buffer = Buffer.empty(AREA);
        new FocusHighlighted(list().id("bases")
                        .focusable()
                        .highlightSymbol("")
                        .items("main", "release")
                        .selected(0))
                .render(Frame.forTesting(buffer), AREA, context);
        return buffer;
    }

    private static boolean reversed(Buffer buffer) {
        return buffer.get(0, 0).style().effectiveModifiers().contains(Modifier.REVERSED);
    }

    @Test
    void withTheFocusTheSelectedRowIsHighlightedAsInUse() {
        assertTrue(reversed(rendered("bases")));
    }

    @Test
    void withTheFocusElsewhereTheSelectedRowIsHighlightedAsNotInUse() {
        assertFalse(reversed(rendered("title")));
    }
}
