package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import java.util.function.BiFunction;

/**
 * A popup as big as the screen allows while still showing it is a popup: the screen less a margin on every side, and
 * no wider than {@code maxWidth}, centered. The popup is only made once its size is known, as what it shows depends
 * on it.
 *
 * @param marginX  the columns left free on the left and on the right
 * @param marginY  the lines left free above and under
 * @param maxWidth the widest the popup gets
 * @param dialog   the popup for a width and a height, which it should fill
 */
public record LargeDialog(int marginX, int marginY, int maxWidth, BiFunction<Integer, Integer, Element> dialog)
        implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        int width = Math.max(0, Math.min(maxWidth, area.width() - 2 * marginX));
        int height = Math.max(0, area.height() - 2 * marginY);
        Rect popup = new Rect(area.x() + (area.width() - width) / 2, area.y() + marginY, width, height);
        context.renderChild(dialog.apply(width, height), frame, popup);
    }

    @Override
    public Constraint constraint() {
        return Constraint.fill();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return Size.of(availableWidth, availableHeight);
    }
}
