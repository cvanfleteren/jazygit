package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;

/**
 * A title in the bottom border of a bordered element, on the right, as the title is shown in the top one.
 */
public final class BottomTitle {

    private BottomTitle() {}

    /**
     * Draws {@code title} on the bottom border of {@code area}, styled as the title in its top border, which is
     * already drawn; a title that does not fit is left out.
     */
    public static void draw(Frame frame, Rect area, String title) {
        int x = area.right() - 1 - title.length();
        int y = area.bottom() - 1;
        if (area.height() >= 2 && x > area.x()) {
            frame.buffer()
                    .setString(
                            x,
                            y,
                            title,
                            frame.buffer().get(area.x() + 1, area.y()).style());
        }
    }
}
