package net.vanfleteren.jazygit.features.changes.panes.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.widgets.tree.TreeNode;
import io.vavr.collection.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * A tree drawn without its guide lines, but with its indent.
 */
class UnguidedTest {

    private static final Rect AREA = new Rect(0, 0, 20, 4);

    private static TreeNode<String> root() {
        return TreeNode.<String>of("root")
                .add(TreeNode.<String>of("dir")
                        .add(TreeNode.<String>of("deeper").leaf())
                        .expanded())
                .add(TreeNode.<String>of("b").leaf())
                .expanded();
    }

    /**
     * The rows {@code root} is drawn as, its labels drawn as they are, as the file tree draws its nodes.
     */
    private static List<String> lines(TreeNode<String> root) {
        AtomicReference<List<StyledElement<?>>> drawn = new AtomicReference<>(List.empty());
        TreeElement<String> tree = Toolkit.<String>tree(root)
                .indentWidth(2)
                .highlightSymbol("")
                .nodeRenderer(node -> {
                    StyledElement<?> label = Toolkit.text(node.label());
                    drawn.updateAndGet(elements -> elements.prepend(label));
                    return label;
                });
        return lines(new Unguided(tree, () -> drawn.get().map(StyledElement::renderedArea)));
    }

    private static List<String> lines(Element unguided) {
        Buffer buffer = Buffer.empty(AREA);
        unguided.render(Frame.forTesting(buffer), AREA, RenderContext.empty());
        return List.range(AREA.top(), AREA.bottom())
                .map(y -> List.range(AREA.left(), AREA.right())
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString()
                        .stripTrailing());
    }

    @Test
    void theGuidesAreBlankedAndEachLevelKeepsItsIndent() {
        assertEquals(List.of("▼ root", "  ▼ dir", "    deeper", "  b"), lines(root()));
    }

    @Test
    void aLabelStartingWithBlanksKeepsTheGuideSymbolsAfterThem() {
        // As an unchanged file of all files is drawn: blanks where a changed one has its status.
        TreeNode<String> root = TreeNode.<String>of("root")
                .add(TreeNode.<String>of("   ─x").leaf())
                .expanded();

        assertEquals("     ─x", lines(root).get(1));
    }

    @Test
    void aRowWithoutANodeIsLeftAsItIs() {
        // Such as a row below the last node that only the scrollbar, drawn as "│", crosses.
        assertEquals(
                "   │", lines(new Unguided(Toolkit.text("   │"), List::empty)).head());
    }
}
