package net.vanfleteren.jazygit.kernel.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.widgets.tree.TreeNode;
import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

/**
 * A tree drawn without its guide lines, but with its indent.
 */
class UnguidedTest {

    private static final Rect AREA = new Rect(0, 0, 20, 4);

    private static TreeElement<String> tree() {
        TreeNode<String> root = TreeNode.<String>of("root")
                .add(TreeNode.<String>of("dir")
                        .add(TreeNode.<String>of("deeper").leaf())
                        .expanded())
                .add(TreeNode.<String>of("b").leaf())
                .expanded();
        return Toolkit.<String>tree(root).indentWidth(2).highlightSymbol("");
    }

    private static List<String> lines(Element element) {
        Buffer buffer = Buffer.empty(AREA);
        new Unguided(element).render(Frame.forTesting(buffer), AREA, RenderContext.empty());
        return List.range(AREA.top(), AREA.bottom())
                .map(y -> List.range(AREA.left(), AREA.right())
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString()
                        .stripTrailing());
    }

    @Test
    void theGuidesAreBlankedAndEachLevelKeepsItsIndent() {
        assertEquals(List.of("▼ root", "  ▼ dir", "    deeper", "  b"), lines(tree()));
    }

    @Test
    void cellsAfterTheNodeKeepTheirGuideSymbols() {
        assertEquals("a ├─ b", lines(Toolkit.text("a ├─ b")).head());
    }

    @Test
    void aRowWithoutANodeIsLeftAsItIs() {
        // Such as a row below the last node that only the scrollbar, drawn as "│", crosses.
        assertEquals("   │", lines(Toolkit.text("   │")).head());
    }
}
