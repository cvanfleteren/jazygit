package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.RichTextState;
import io.vavr.collection.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link Viewport} scrolls as TamboUI's text area does: the keys and the wheel work out where an area goes as
 * a value, and only tell its text area the row they come to, so a TamboUI that clamped otherwise must fail here.
 */
class ViewportTest {

    private static final List<Scroll> SCROLLS = List.of(
            new Scroll("up", RichTextState::scrollUp, view -> view.by(-1)),
            new Scroll("down", RichTextState::scrollDown, view -> view.by(1)),
            new Scroll("wheel up", state -> state.scrollUp(3), view -> view.by(-3)),
            new Scroll("wheel down", state -> state.scrollDown(3), view -> view.by(3)),
            new Scroll("page up", RichTextState::pageUp, Viewport::pageUp),
            new Scroll("page down", RichTextState::pageDown, Viewport::pageDown),
            new Scroll("bottom", RichTextState::scrollToBottom, Viewport::bottom),
            new Scroll(
                    "to row 7",
                    state -> {
                        state.scrollToTop();
                        state.scrollDown(7);
                    },
                    view -> view.at(7)));

    @Test
    void scrollsAndClampsAsTheTextAreaDoes() {
        for (int lines = 0; lines < 40; lines++) {
            for (int height = 0; height < 15; height++) {
                for (int start = 0; start < 40; start++) {
                    for (Scroll scroll : SCROLLS) {
                        RichTextState area = area(lines, height, start);
                        Viewport view = Viewport.of(area);
                        scroll.area().accept(area);
                        assertEquals(
                                area.scrollRow(),
                                scroll.view().apply(view).top(),
                                scroll.name() + " from " + start + " in " + lines + " lines, " + height + " high");
                    }
                }
            }
        }
    }

    @Test
    void readsWhereTheTextAreaIsAndWhatOfItShows() {
        RichTextState area = area(30, 10, 12);
        area.setViewportWidth(80);

        assertEquals(new Viewport(12, 30, 80, 10), Viewport.of(area));
    }

    private static RichTextState area(int lines, int height, int start) {
        RichTextState area = new RichTextState();
        area.setViewportHeight(height);
        area.setContentHeight(lines);
        area.scrollDown(start);
        return area;
    }

    /**
     * A way to scroll, as the text area does it and as the view does it.
     */
    private record Scroll(String name, Consumer<RichTextState> area, UnaryOperator<Viewport> view) {}
}
