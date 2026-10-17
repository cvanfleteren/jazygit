package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import io.vavr.control.Option;
import java.util.Optional;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme.Backdrop;
import org.junit.jupiter.api.Test;

/**
 * What is behind a popup, dimmed.
 */
class DimmedTest {

    private static final Rect AREA = new Rect(0, 0, 40, 10);
    private static final Color MUTED = Color.rgb(0x80, 0x80, 0x80);
    private static final Backdrop TERMINAL_DEFAULT = new Backdrop(Option.none(), Style.EMPTY.fg(MUTED));

    private static Buffer render(Element element) {
        Buffer buffer = Buffer.empty(AREA);
        element.render(Frame.forTesting(buffer), AREA, RenderContext.empty());
        return buffer;
    }

    private static boolean dim(Buffer buffer, int x, int y) {
        return buffer.get(x, y).style().addModifiers().contains(Modifier.DIM);
    }

    @Test
    void whatIsBehindTakesNoFocus() throws Exception {
        Element screen = stack(
                new Dimmed(panel(text("files")).id("files").focusable(), TERMINAL_DEFAULT),
                panel(text("popup")).id("popup").focusable());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> screen)) {
            SettledPilot.of(runner).pause();

            assertEquals(
                    java.util.List.of("popup"), runner.runner().focusManager().focusOrder());
        }
    }

    @Test
    void plainTextTakesTheBackdropColourAndIsDimmed() {
        Buffer buffer = render(new Dimmed(text("plain"), TERMINAL_DEFAULT));

        assertEquals(Optional.of(MUTED), buffer.get(0, 0).style().fg());
        assertTrue(dim(buffer, 0, 0));
        assertTrue(dim(buffer, AREA.width() - 1, AREA.height() - 1));
    }

    @Test
    void colouredTextKeepsItsColourAndIsDimmed() {
        Buffer buffer = render(new Dimmed(text("red").red(), TERMINAL_DEFAULT));

        assertEquals(Optional.of(Color.RED), buffer.get(0, 0).style().fg());
        assertTrue(dim(buffer, 0, 0));
    }

    @Test
    void textInTheThemesPlainColourCountsAsPlain() {
        Color themeFg = Color.rgb(0xa9, 0xb7, 0xc6);
        Backdrop backdrop = new Backdrop(Option.of(themeFg), Style.EMPTY.fg(MUTED));

        Buffer buffer = render(
                new Dimmed(row(text("plain").fg(themeFg).length(5), text("red").red()), backdrop));

        assertEquals(Optional.of(MUTED), buffer.get(0, 0).style().fg());
        assertEquals(Optional.of(Color.RED), buffer.get(5, 0).style().fg());
    }

    @Test
    void aPopupOnTopIsNotDimmed() {
        Buffer buffer = render(stack(new Dimmed(text("behind"), TERMINAL_DEFAULT), dialog("Popup", text("front"))));

        assertTrue(dim(buffer, 0, 0));
        assertFalse(dim(buffer, AREA.width() / 2, AREA.height() / 2));
        assertNotEquals(buffer.get(AREA.width() / 2, AREA.height() / 2).style().fg(), Optional.of(MUTED));
    }
}
