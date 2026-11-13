package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * {@code panel} bordered as a focused left pane while one of the elements {@code focusIds} in it has the focus, as the
 * themes border a focused text field. The focus is only known as the panel is rendered, so it is told then.
 */
public record FocusBordered(Panel panel, List<String> focusIds) implements Element {

    /**
     * {@code panel} bordered as focused while the one element {@code focusId} in it has the focus.
     */
    public FocusBordered(Panel panel, String focusId) {
        this(panel, List.of(focusId));
    }

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        panel.toggleClass(Theme.Classes.FOCUSED, focusIds.exists(context::isFocused));
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
