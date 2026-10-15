package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A popup with the details of what is selected in it in a box of its own right under it, as wide as the popup.
 * The popup stays where it is whatever the box shows: it is placed so that the tallest of the details it can show
 * fits under it, and is cut to leave room for them when the screen is too low.
 *
 * @param dialog   the popup, which centers itself in the area it is given
 * @param shown    the details shown now, if any; no box when there are none
 * @param possible every details text the popup can show, to leave room for the tallest
 */
public record WithDetails(Element dialog, Option<String> shown, List<String> possible) implements Element {

    // The lines the border of the box takes.
    private static final int BORDER = 2;

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        Size size = dialog.preferredSize(area.width(), area.height(), context);
        int width = Math.min(size.widthOr(area.width()), area.width());
        int room = possible.map(text -> boxHeight(text, width)).max().getOrElse(0);
        int height = Math.max(0, Math.min(size.heightOr(area.height()), area.height() - room));
        // Centered, unless that leaves too little room for the box under it.
        int top = Math.max(0, Math.min((area.height() - height) / 2, area.height() - height - room));
        Rect popup = new Rect(area.x(), area.y() + top, area.width(), height);
        context.renderChild(dialog, frame, popup);
        shown.forEach(text -> {
            int boxHeight = Math.min(boxHeight(text, width), area.bottom() - popup.bottom());
            context.renderChild(box(text, width, boxHeight), frame,
                    new Rect(area.x(), popup.bottom(), area.width(), boxHeight));
        });
    }

    @Override
    public Constraint constraint() {
        return Constraint.fill();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return dialog.preferredSize(availableWidth, availableHeight, context);
    }

    private static int boxHeight(String text, int width) {
        return DialogFrame.wrappedHeight(text, width, Integer.MAX_VALUE) + BORDER;
    }

    private static Element box(String text, int width, int height) {
        return Toolkit.dialog(DialogFrame.wrapped(text, width, Integer.MAX_VALUE)).rounded().width(width).padding(1)
                .length(height);
    }
}
