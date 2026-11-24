package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.textArea;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.widgets.input.TextAreaState;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * A text area wraps a long line over as many rows as it takes, and can mark the column past a line length (ADR 193).
 */
class SubmittableTextAreaTest {

    // Bordered, so the text starts at column 1 and 18 columns are left for it.
    private static final Rect AREA = new Rect(0, 0, 20, 6);

    @Test
    void aLongLineWrapsAtAWordOverTheNextRowsAndKeepsNoLineBreak() {
        TextAreaState state = new TextAreaState();
        state.setText("the quick brown fox jumps over the lazy dog");

        Buffer buffer = render(area(state), ThemeName.DARK);

        assertEquals("the quick brown", row(buffer, 1).strip());
        assertEquals("fox jumps over the", row(buffer, 2).strip());
        assertEquals("lazy dog", row(buffer, 3).strip());
        assertEquals("the quick brown fox jumps over the lazy dog", state.text());
    }

    @Test
    void theRulerTintsTheColumnPastTheLineLengthOnEveryRowOfText() {
        TextAreaState state = new TextAreaState();
        state.setText("a line longer than ten");

        Buffer buffer = render(area(state).ruler(10), ThemeName.DARK);

        Style ruler = rulerOf(ThemeName.DARK);
        // The text starts after the left border, so the 11th column of text is column 11 of the buffer.
        IntStream.range(1, AREA.height() - 1)
                .forEach(y -> assertEquals(ruler.bg(), buffer.get(11, y).style().bg(), "row " + y));
        assertNotEquals(ruler.bg(), buffer.get(10, 1).style().bg());
        assertNotEquals(ruler.bg(), buffer.get(11, 0).style().bg());
        assertNotEquals(ruler.bg(), buffer.get(11, AREA.height() - 1).style().bg());
        // The text under the ruler is still drawn: the 11th character of "a line longer".
        assertEquals("g", buffer.get(11, 1).symbol());
    }

    @Test
    void aThemeWithoutColoursReversesTheRuler() {
        TextAreaState state = new TextAreaState();

        Buffer buffer = render(area(state).ruler(10), ThemeName.NONE);

        assertEquals(
                Style.EMPTY.reversed().addModifiers(), buffer.get(11, 1).style().addModifiers());
    }

    @Test
    void aRulerPastTheBoxIsNotDrawn() {
        TextAreaState state = new TextAreaState();

        Buffer plain = render(area(state), ThemeName.DARK);
        Buffer ruled = render(area(new TextAreaState()).ruler(40), ThemeName.DARK);

        assertEquals(plain, ruled);
    }

    private static SubmittableTextArea area(TextAreaState state) {
        return new SubmittableTextArea(textArea(state).title("Body"), state, "body", () -> {});
    }

    private static Style rulerOf(ThemeName theme) {
        StyleEngine engine = engine(theme);
        return Theme.spanStyles(engine).of(Theme.Classes.RULER);
    }

    private static StyleEngine engine(ThemeName theme) {
        StyleEngine engine = Theme.createEngine().get();
        Theme.activate(engine, theme);
        return engine;
    }

    private static Buffer render(SubmittableTextArea view, ThemeName theme) {
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        context.setStyleEngine(engine(theme));
        Buffer buffer = Buffer.empty(AREA);
        view.render(Frame.forTesting(buffer), AREA, context);
        return buffer;
    }

    private static String row(Buffer buffer, int y) {
        return IntStream.range(1, AREA.width() - 1)
                .mapToObj(x -> buffer.get(x, y).symbol())
                .collect(Collectors.joining());
    }
}
