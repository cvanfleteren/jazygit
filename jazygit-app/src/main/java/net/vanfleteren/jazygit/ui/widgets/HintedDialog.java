package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.DialogElement;

/**
 * {@code dialog} with {@code hint} in its bottom border, on the right.
 */
public record HintedDialog(DialogElement dialog, String hint) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        dialog.render(frame, area, context);
        if (!area.isEmpty()) {
            BottomTitle.draw(frame, drawn(area, dialog.preferredSize(area.width(), area.height(), context)), hint);
        }
    }

    /**
     * Where a dialog of {@code size} is drawn in {@code area}: centred in it, as the dialog does, and cut to fit.
     */
    static Rect drawn(Rect area, Size size) {
        int width = Math.min(size.widthOr(area.width()), area.width());
        int height = Math.min(size.heightOr(area.height()), area.height());
        return new Rect(area.x() + (area.width() - width) / 2, area.y() + (area.height() - height) / 2, width, height);
    }

    @Override
    public Constraint constraint() {
        return dialog.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return dialog.preferredSize(availableWidth, availableHeight, context);
    }
}
