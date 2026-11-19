package net.vanfleteren.jazygit.content;

import dev.tamboui.style.RichTextState;

/**
 * How far down an area is scrolled, and how much of it shows, as its text area last had it: what the keys and the
 * mouse scroll, worked out as a value. It clamps as the text area does, so the area's element is only told the row it
 * comes to.
 *
 * @param top    the first line shown
 * @param lines  how many lines the area has
 * @param width  how many columns show
 * @param height how many lines show
 */
record Viewport(int top, int lines, int width, int height) {

    /**
     * Where the text area of {@code state} is, and what of it shows.
     */
    static Viewport of(RichTextState state) {
        return new Viewport(state.scrollRow(), state.contentHeight(), state.viewportWidth(), state.viewportHeight());
    }

    /**
     * Scrolled to {@code row}, as near as the lines let it go.
     */
    Viewport at(int row) {
        return new Viewport(Math.max(0, Math.min(row, lastTop())), lines, width, height);
    }

    /**
     * Scrolled {@code by} lines further down, or up when negative.
     */
    Viewport by(int by) {
        return at(top + by);
    }

    Viewport pageDown() {
        return by(Math.max(1, height - 1));
    }

    Viewport pageUp() {
        return by(-Math.max(1, height - 1));
    }

    Viewport bottom() {
        return at(lastTop());
    }

    private int lastTop() {
        return Math.max(0, lines - height);
    }
}
