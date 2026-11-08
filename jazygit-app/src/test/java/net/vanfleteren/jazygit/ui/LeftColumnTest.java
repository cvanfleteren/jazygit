package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LeftColumnTest {

    private static final Rect AREA = new Rect(0, 0, 40, 60);

    private final LeftColumn leftColumn =
            new LeftColumn(msg -> {}, StyleEngine.create(), Clock.systemUTC(), id -> {}, tab -> {});

    // The keys that resize and maximize a pane find the focused one by its place in Scopes.leftPanes (ADR 92).
    @ParameterizedTest
    @EnumSource(Screen.class)
    void thePanesShowTopToBottomInTheOrderTheKeysCountThem(Screen screen) {
        ElementRegistry registry = new ElementRegistry();
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, registry));
        leftColumn
                .render(TestModels.loaded().withScreen(screen), "", "", Option.none())
                .column()
                .render(Frame.forTesting(Buffer.empty(AREA)), AREA, context);

        List<String> panes = Scopes.leftPanes(screen);
        List<Integer> tops = panes.map(id -> registry.getArea(id).y());
        assertEquals(tops.sorted(), tops, "the tops of " + panes);
        assertEquals(tops.size(), tops.distinct().size(), "the tops of " + panes);
    }
}
