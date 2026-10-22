package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import io.vavr.control.Option;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A list in a popup, whose picked row shows whether or not it has the focus.
 */
class FocusHighlightedTest {

    private static final Rect AREA = new Rect(0, 0, 20, 3);
    private static final String THEME = ".selection { background: green; } .inactive-selection { background: blue; }";

    /**
     * The list of two rows, the first selected, rendered while {@code focused} has the focus.
     */
    private static Buffer rendered(String focused) {
        return rendered(focused, Option.none());
    }

    /**
     * The list of two rows, the first selected, rendered while {@code focused} has the focus, in {@code theme}.
     */
    private static Buffer rendered(String focused, Option<String> theme) {
        FocusManager focus = new FocusManager();
        focus.setFocus(focused);
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        theme.forEach(css -> {
            StyleEngine engine = StyleEngine.create();
            engine.addStylesheet("theme", css);
            engine.setActiveStylesheet("theme");
            context.setStyleEngine(engine);
        });
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
    void withTheFocusElsewhereAndNoInactiveSelectionTheSelectedRowIsStillHighlighted() {
        assertTrue(reversed(rendered("title")));
    }

    @Test
    void withTheFocusTheSelectedRowTakesTheThemesSelection() {
        assertEquals(Optional.of(Color.GREEN), background(rendered("bases", Option.of(THEME))));
    }

    @Test
    void withTheFocusElsewhereTheSelectedRowTakesTheThemesInactiveSelection() {
        Buffer buffer = rendered("title", Option.of(THEME));

        assertEquals(Optional.of(Color.BLUE), background(buffer));
        assertFalse(reversed(buffer));
    }

    private static Optional<Color> background(Buffer buffer) {
        return buffer.get(0, 0).style().bg();
    }
}
