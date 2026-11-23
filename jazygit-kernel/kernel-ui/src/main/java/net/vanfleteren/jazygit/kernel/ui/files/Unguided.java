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

/**
 * {@code tree} drawn without its guide lines, but with its indent: TamboUI's tree drops the indent along with the
 * guides when it draws none, so it draws them and they are blanked after. Only the cells in front of a row's node are
 * blanked; a row without a node, such as one below the last that only
 * the scrollbar crosses, is left as it is. The tree is otherwise as it is wrapped: its id, focus and events are its
 * own.
 */
record Unguided(Element tree) implements Element {

    // The cells of TamboUI's default guides, two a level.
    private static final List<String> GUIDES = List.of("├", "└", "│", "─");

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        tree.render(frame, area, context);
        Buffer buffer = frame.buffer();
        Rect drawn = buffer.area().intersection(area);
        List.range(drawn.top(), drawn.bottom()).forEach(y -> unguide(buffer, drawn, y));
    }

    private static void unguide(Buffer buffer, Rect drawn, int y) {
        List<Integer> columns = List.range(drawn.left(), drawn.right());
        List<Integer> prefix = columns.takeWhile(x -> inPrefix(buffer.get(x, y).symbol()));
        if (prefix.size() < columns.size()) {
            prefix.filter(x -> GUIDES.contains(buffer.get(x, y).symbol()))
                    .forEach(x -> buffer.set(x, y, buffer.get(x, y).symbol(" ")));
        }
    }

    private static boolean inPrefix(String symbol) {
        return symbol.isBlank() || GUIDES.contains(symbol);
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
