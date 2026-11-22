package net.vanfleteren.jazygit.kernel.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.widgets.LabelledLines.Line;
import org.junit.jupiter.api.Test;

/**
 * How the checks of a commit are counted, as every forge draws them (ADR 175).
 */
class CheckSymbolsTest {

    @Test
    void theChecksAreCountedByStateLeavingOutThoseWithNone() throws Exception {
        assertEquals("✗ 1 failed · ✓ 3 passed", line(CheckSymbols.counts(1, 0, 3)));
        assertEquals("● 2 running", line(CheckSymbols.counts(0, 2, 0)));
        assertTrue(CheckSymbols.counts(0, 0, 0).isEmpty());
    }

    @Test
    void aPartWithoutPiecesIsLeftOutWithItsSeparator() throws Exception {
        assertEquals(
                "done in ~3m",
                line(CheckSymbols.joined(List.of(CheckSymbols.counts(0, 0, 0), List.of(text("done in ~3m"))))));
        assertEquals(
                "● 1 running · done in ~3m",
                line(CheckSymbols.joined(List.of(CheckSymbols.counts(0, 1, 0), List.of(text("done in ~3m"))))));
    }

    @Test
    void aCheckByItsNameIsCutToTheWidthGivenAndLeftOutWithoutRoom() throws Exception {
        assertEquals("✗ accep…", line(CheckSymbols.named(CheckSymbols.FAILED, "acceptance", 8)));
        assertEquals("● lint", line(CheckSymbols.named(CheckSymbols.RUNNING, "lint", 8)));
        assertEquals("✓", line(CheckSymbols.named(CheckSymbols.PASSED, "lint", 2)));
    }

    private static String line(List<StyledElement<?>> pieces) throws Exception {
        return LabelledLinesTest.screen(List.of(Line.pieces(pieces))).get(0).stripTrailing();
    }
}
