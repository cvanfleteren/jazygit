package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import java.util.function.IntFunction;

/**
 * A popup {@code width} wide, or as wide as the screen when that is narrower. The popup is only made once its width
 * is known, as its height depends on it: text wrapped at a width it does not get would wrap over more lines than it
 * has room for, and lose its last ones.
 *
 * @param width  the width the popup asks for
 * @param dialog the popup for a width, which centers itself in the area it is given
 */
public record FittedDialog(int width, IntFunction<? extends Element> dialog) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        context.renderChild(at(area.width()), frame, area);
    }

    @Override
    public Constraint constraint() {
        return Constraint.fill();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return at(availableWidth).preferredSize(availableWidth, availableHeight, context);
    }

    /**
     * The popup for {@code available} columns; for its own width when what is available is not known.
     */
    private Element at(int available) {
        return dialog.apply(available < 0 ? width : Math.min(width, available));
    }
}
