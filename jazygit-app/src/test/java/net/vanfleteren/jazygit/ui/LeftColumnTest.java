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
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.preferences.Screen;
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

    @ParameterizedTest
    @EnumSource(Screen.class)
    void aCollapsedColumnIsAnEmptyPanelMarkedAtItsTopRight(Screen screen) {
        Rect area = new Rect(0, 0, LeftColumn.COLLAPSED_COLUMNS, 5);
        ElementRegistry registry = new ElementRegistry();
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, registry));
        Buffer buffer = Buffer.empty(area);
        LeftColumn.Rendered rendered = leftColumn.render(
                TestModels.loaded()
                        .withScreen(screen)
                        .mapPreferences(preferences -> preferences.withLeftCollapsed(screen, true)),
                "",
                "",
                Option.none());
        rendered.column().render(Frame.forTesting(buffer), area, context);

        // Square without a theme, which rounds the corners.
        assertEquals(List.of("┌»┐", "│ │", "│ │", "│ │", "└─┘"), lines(buffer, area));
        assertEquals(List.empty(), rendered.panes());
        Scopes.leftPanes(screen).forEach(id -> assertNull(registry.getArea(id), id + " is not drawn"));
    }

    private static List<String> lines(Buffer buffer, Rect area) {
        return List.range(area.y(), area.bottom())
                .map(y -> List.range(area.x(), area.right())
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString());
    }
}
