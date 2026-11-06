package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import io.vavr.collection.Traversable;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.image.Sharp;

/**
 * {@code content}, and the images of {@code shown} its cells are of sent to {@code terminal} with the Kitty graphics
 * protocol, those that were not yet (ADR 86): it is to be the whole screen, so that a cell covered by a popup or changed
 * after it is drawn is as it is shown.
 */
public record ImagesSent(Element content, Traversable<Sharp> shown, KittyGraphics graphics, Consumer<String> terminal)
        implements Element {

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        content.render(frame, area, context);
        graphics.sendFor(frame.buffer(), shown, terminal);
    }

    @Override
    public Constraint constraint() {
        return content.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return content.preferredSize(availableWidth, availableHeight, context);
    }
}
