package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;

/**
 * {@code element}, drawn as it is, which says it is {@code height} lines high without measuring it. TamboUI's list asks
 * every item for its size on every frame, the items scrolled out of sight too, and a line of text measures each of its
 * characters: for thousands of long rows, as the commits with a wide graph, that was most of the time a key press
 * took. The list only uses the height; the width is left unknown.
 */
final class KnownHeight extends StyledElement<KnownHeight> {

    private final Element element;
    private final int height;

    KnownHeight(Element element, int height) {
        this.element = element;
        this.height = height;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return Size.heightOnly(height);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        context.renderChild(element, frame, area);
    }
}
