package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

class DialogFrameTest {

    private static final Rect AREA = new Rect(0, 0, 40, 16);

    @Test
    void aPopupTakesTheRoundedBorderOfAColouredTheme() {
        Buffer screen = render(DialogFrame.of("Title", 20, text("hello")), ThemeName.DARK);

        assertEquals(1, count(screen, "╭"));
        assertEquals(0, count(screen, "┌"));
    }

    @Test
    void aPopupTakesThePlainBorderOfTheThemeWithoutStyles() {
        Buffer screen = render(DialogFrame.of("Title", 20, text("hello")), ThemeName.NONE);

        assertEquals(0, count(screen, "╭"));
        assertEquals(1, count(screen, "┌"));
    }

    @Test
    void theBoxWithDetailsUnderAPopupTakesTheThemesBorderToo() {
        Element popup =
                new WithDetails(DialogFrame.of("Title", 20, text("hello")), Option.of("details"), List.of("details"));

        assertEquals(2, count(render(popup, ThemeName.DARK), "╭"));
        assertEquals(2, count(render(popup, ThemeName.NONE), "┌"));
    }

    private static Buffer render(Element view, ThemeName theme) {
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        StyleEngine engine = Theme.createEngine().get();
        Theme.activate(engine, theme);
        context.setStyleEngine(engine);
        Buffer buffer = Buffer.empty(AREA);
        view.render(Frame.forTesting(buffer), AREA, context);
        return buffer;
    }

    private static int count(Buffer buffer, String symbol) {
        int found = 0;
        for (int y = 0; y < AREA.height(); y++) {
            for (int x = 0; x < AREA.width(); x++) {
                if (buffer.get(x, y).symbol().equals(symbol)) {
                    found++;
                }
            }
        }
        return found;
    }
}
