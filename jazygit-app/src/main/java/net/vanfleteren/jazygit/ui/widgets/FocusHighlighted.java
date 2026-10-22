package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.ListElement;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.Theme.Highlights;

/**
 * {@code list} with its selected row highlighted as in the left panes: as the theme's selection while the list has
 * the focus, and as its inactive selection while another field of the popup has it. Unlike in a left pane, the row
 * picked in a popup must always show, so a theme without an inactive selection highlights it as in use; the border of
 * the field tells where the focus is. The focus is only known as the list is rendered, so it is told then.
 */
public record FocusHighlighted(ListElement<?> list) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        Highlights highlights = context instanceof DefaultRenderContext rendering
                ? rendering.styleEngine().map(Theme::highlights).orElse(Highlights.DEFAULT)
                : Highlights.DEFAULT;
        Style inactive = highlights.of(false);
        list.highlightStyle(
                context.isFocused(list.id()) || inactive.equals(Style.EMPTY) ? highlights.of(true) : inactive);
        list.render(frame, area, context);
    }

    @Override
    public Constraint constraint() {
        return list.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return list.preferredSize(availableWidth, availableHeight, context);
    }
}
