package net.vanfleteren.jazygit.kernel.ui.widgets;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.Lazy;

/**
 * {@code element}, drawn as it is, which says it is {@code height} lines high without measuring it. TamboUI's list asks
 * every item for its size on every frame, the items scrolled out of sight too, and a line of text measures each of its
 * characters: for thousands of long rows, as the commits with a wide graph, that was most of the time a key press
 * took. The list only uses the height; the width is left unknown. The element is only built the first time it is
 * drawn, so the rows of a long list that are never scrolled to are never built.
 */
public final class KnownHeight extends StyledElement<KnownHeight> {

    private final Lazy<? extends Element> element;
    private final int height;

    KnownHeight(Lazy<? extends Element> element, int height) {
        this.element = element;
        this.height = height;
    }

    /** {@code element}, one line high, built only once drawn. */
    public static KnownHeight line(java.util.function.Supplier<? extends Element> element) {
        return new KnownHeight(Lazy.of(element), 1);
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return Size.heightOnly(height);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        context.renderChild(element.get(), frame, area);
    }
}
