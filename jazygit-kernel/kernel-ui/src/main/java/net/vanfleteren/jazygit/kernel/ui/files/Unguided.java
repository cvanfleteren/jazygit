package net.vanfleteren.jazygit.kernel.ui.files;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.KeyEventHandler;
import dev.tamboui.toolkit.event.MouseEventHandler;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.collection.List;
import java.util.function.Supplier;

/**
 * {@code tree} drawn without its guide lines, but with its indent: TamboUI's tree drops the indent along with the
 * guides when it draws none, so it draws them and they are blanked after. Only the cells in front of where a row's
 * node was drawn are blanked, so a label keeps its own text, even one that starts with blanks and a guide symbol,
 * and a row without a node, such as one that only the scrollbar crosses, is left as it is. The tree is otherwise as
 * it is wrapped: its id, focus and events are its own.
 *
 * @param nodes where the nodes of the frame being rendered were drawn, once {@code tree} is rendered: null for one
 *              that was not
 */
record Unguided(Element tree, Supplier<List<Rect>> nodes) implements Element {

    // The cells of TamboUI's default guides, two a level.
    private static final List<String> GUIDES = List.of("├", "└", "│", "─");

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        tree.render(frame, area, context);
        Buffer buffer = frame.buffer();
        Rect drawn = buffer.area().intersection(area);
        nodes.get()
                .filter(node -> node != null && node.top() >= drawn.top() && node.top() < drawn.bottom())
                .forEach(node -> unguide(buffer, drawn.left(), Math.min(node.left(), drawn.right()), node.top()));
    }

    /**
     * Blanks the guide cells of row {@code y} from column {@code left} up to {@code node}, where its node starts.
     */
    private static void unguide(Buffer buffer, int left, int node, int y) {
        List.range(left, node)
                .filter(x -> GUIDES.contains(buffer.get(x, y).symbol()))
                .forEach(x -> buffer.set(x, y, buffer.get(x, y).symbol(" ")));
    }

    @Override
    public Constraint constraint() {
        return tree.constraint();
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return tree.preferredSize(availableWidth, availableHeight, context);
    }

    @Override
    public boolean isFocusable() {
        return tree.isFocusable();
    }

    @Override
    public String id() {
        return tree.id();
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        return tree.handleKeyEvent(event, focused);
    }

    @Override
    public EventResult handlePasteEvent(PasteEvent event) {
        return tree.handlePasteEvent(event);
    }

    @Override
    public EventResult handleMouseEvent(MouseEvent event) {
        return tree.handleMouseEvent(event);
    }

    @Override
    public KeyEventHandler keyEventHandler() {
        return tree.keyEventHandler();
    }

    @Override
    public MouseEventHandler mouseEventHandler() {
        return tree.mouseEventHandler();
    }

    @Override
    public boolean isDraggable() {
        return tree.isDraggable();
    }

    @Override
    public Rect renderedArea() {
        return tree.renderedArea();
    }
}
