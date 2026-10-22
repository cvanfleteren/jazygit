package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * {@code panel} bordered as a focused left pane while the element {@code focusId} in it has the focus, as the themes
 * border a focused text field. The focus is only known as the panel is rendered, so it is told then.
 */
public record FocusBordered(Panel panel, String focusId) implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        panel.toggleClass(Theme.Classes.FOCUSED, context.isFocused(focusId));
        panel.render(frame, area, context);
    }

    @Override
    public Constraint constraint() {
        return panel.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return panel.preferredSize(availableWidth, availableHeight, context);
    }
}
