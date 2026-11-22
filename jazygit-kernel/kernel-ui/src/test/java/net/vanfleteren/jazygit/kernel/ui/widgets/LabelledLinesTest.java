package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LabelledLines.Line;
import org.junit.jupiter.api.Test;

/**
 * The labelled rows of a dialog that shows a change, and the checks of a commit, as every forge draws them (ADR 175).
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

    @Test
    void theChecksAreCountedByStateLeavingOutThoseWithNone() throws Exception {
        assertEquals("✗ 1 failed · ✓ 3 passed", line(CheckSymbols.counts(1, 0, 3)));
        assertEquals("● 2 running", line(CheckSymbols.counts(0, 2, 0)));
        assertTrue(CheckSymbols.counts(0, 0, 0).isEmpty());
    }

    @Test
    void aCheckByItsNameIsCutToTheWidthGiven() throws Exception {
        assertEquals("✗ accep…", line(CheckSymbols.named(CheckSymbols.FAILED, "acceptance", 8)));
        assertEquals("● lint", line(CheckSymbols.named(CheckSymbols.RUNNING, "lint", 8)));
    }

    private static String line(List<StyledElement<?>> pieces) throws Exception {
        return screen(List.of(Line.pieces(pieces))).get(0).stripTrailing();
    }

    private static List<String> screen(List<Line> lines) throws Exception {
        Element shown = column(lines.map(Line::element).toJavaArray(Element[]::new));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> shown)) {
            return List.of(RenderedText.of(runner, () -> shown).split("\n"));
        }
    }
}
