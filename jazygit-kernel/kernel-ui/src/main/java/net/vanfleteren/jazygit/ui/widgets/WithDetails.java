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
 * fits under it, and is cut to leave room for them when the screen is too low, unless it is one that cannot be cut:
 * then the box shows only when it fits under the whole popup.
 *
 * @param dialog   the popup, which centers itself in the area it is given
 * @param shown    the details shown now, if any; no box when there are none
 * @param possible every details text the popup can show, to leave room for the tallest
 * @param cuts     whether the popup is cut to leave room for the box, for one that scrolls
 */
public record WithDetails(Element dialog, Option<String> shown, List<String> possible, boolean cuts)
        implements Element {

    // The lines the border of the box takes.
    private static final int BORDER = 2;

    /**
     * A popup that is cut to leave room for the box when the screen is too low.
     */
    public WithDetails(Element dialog, Option<String> shown, List<String> possible) {
        this(dialog, shown, possible, true);
    }

    /**
     * A popup that is never cut for the box, which then only shows when it fits under it, as for a popup that
     * cannot scroll and is already about as tall as a screen.
     */
    public static WithDetails uncut(Element dialog, Option<String> shown, List<String> possible) {
        return new WithDetails(dialog, shown, possible, false);
    }

    private static int boxHeight(String text, int width) {
        return DialogFrame.wrappedHeight(text, width, Integer.MAX_VALUE) + BORDER;
    }

    private static Element box(String text, int width, int height) {
        return DialogFrame.themed(Toolkit.dialog(DialogFrame.wrapped(text, width, Integer.MAX_VALUE)))
                .width(width)
                .length(height);
    }

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        Size size = dialog.preferredSize(area.width(), area.height(), context);
        int width = Math.min(size.widthOr(area.width()), area.width());
        int tallest = possible.map(text -> boxHeight(text, width)).max().getOrElse(0);
        int wanted = Math.min(size.heightOr(area.height()), area.height());
        // An uncut popup leaves the box only what is under it.
        int room = cuts ? tallest : Math.min(tallest, area.height() - wanted);
        int height = Math.max(0, Math.min(wanted, area.height() - room));
        // Centered, unless that leaves too little room for the box under it.
        int top = Math.max(0, Math.min((area.height() - height) / 2, area.height() - height - room));
        Rect popup = new Rect(area.x(), area.y() + top, area.width(), height);
        context.renderChild(dialog, frame, popup);
        int below = area.bottom() - popup.bottom();
        shown.filter(text -> cuts || boxHeight(text, width) <= below).forEach(text -> {
            int boxHeight = Math.min(boxHeight(text, width), below);
            context.renderChild(
                    box(text, width, boxHeight), frame, new Rect(area.x(), popup.bottom(), area.width(), boxHeight));
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
}
