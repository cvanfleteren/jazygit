package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LabelledLines.Line;
import org.junit.jupiter.api.Test;

/**
 * The labelled rows of a dialog that shows a change, as every forge draws them (ADR 175).
 */
class LabelledLinesTest {

    @Test
    void aLabelIsRightAlignedInItsColumnAndWhatGoesOnStartsUnderTheValue() throws Exception {
        List<String> lines = screen(
                List.of(Line.labelled("panel.files.title", text("value")), Line.indented(List.of(text("under")))));

        assertEquals("      Files  value", lines.get(0).stripTrailing());
        assertEquals("             under", lines.get(1).stripTrailing());
    }

    @Test
    void theLinesAreFilledWithBlanksToTheirHeightButNotCut() {
        List<Line> two = List.of(Line.of(text("a")), Line.wrapped("one two three", Theme.Classes.BOLD, 12, 3));

        assertEquals(2, two.get(1).height());
        // One line and one wrapped on two take three: three blanks fill them to six.
        assertEquals(5, LabelledLines.filled(two, 6).size());
        assertEquals(2, LabelledLines.filled(two, 2).size());
    }

    static List<String> screen(List<Line> lines) throws Exception {
        Element shown = column(lines.map(Line::element).toJavaArray(Element[]::new));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> shown)) {
            return List.of(RenderedText.of(runner, () -> shown).split("\n"));
        }
    }
}
