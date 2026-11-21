package net.vanfleteren.jazygit.kernel.content.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Expansion;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The split view: the old file on the left and the new one on the right (ADR 139).
 */
class SplitHunkTest {

    // An area of 101 columns: two halves of 50, and the rule between them.
    private static final int AREA = 101;
    private static final int HALF = 50;

    private static final String DIFF = """
            diff --git a/DiffStyle.java b/DiffStyle.java
            --- a/DiffStyle.java
            +++ b/DiffStyle.java
            @@ -1,4 +1,5 @@
             enum DiffStyle {
            -    RICH
            +    RICH,
            +    SPLIT
             }
            -int total = 2;
            """;

    @Test
    void linesUpABlockFirstWithFirstAndLeavesTheShorterSideBlank() {
        List<String> rows = shown(drawn(DIFF, AREA), 0);

        assertEquals(
                List.of(
                        " modified  DiffStyle.java",
                        row("1 │   enum DiffStyle {", "1 │   enum DiffStyle {"),
                        row("2 │ -     RICH", "2 │ +     RICH,"),
                        row("", "3 │ +     SPLIT"),
                        row("3 │   }", "4 │   }"),
                        row("4 │ - int total = 2;", "")),
                rows.map(String::stripTrailing));
        // The rule between the halves is in the same column on every row.
        assertTrue(rows.tail().forAll(row -> row.charAt(HALF) == '│'));
    }

    @Test
    void eachHalfThatHoldsALineIsAStopAContextLineOnTheLeftByItsOldNumber() {
        Drawn drawn = drawn(DIFF, AREA);

        assertEquals(
                List.of(
                        new Anchor.File("DiffStyle.java"),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.OLD, 1),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.NEW, 1),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.OLD, 2),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.NEW, 2),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.NEW, 3),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.OLD, 3),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.NEW, 4),
                        new Anchor.OnLine("DiffStyle.java", DiffSide.OLD, 4),
                        new Anchor.Gap("DiffStyle.java", 6, Option.none())),
                drawn.stops().map(stop -> stop.mark().anchor()));
        // Both halves of a row start on it, each on its side.
        assertEquals(
                List.of(1, 1, 2, 2, 3, 4, 4, 5), drawn.stops().tail().init().map(Drawn.Stop::row));
        assertEquals(
                List.of(DiffSide.OLD, DiffSide.NEW),
                drawn.stops().drop(1).take(2).flatMap(stop -> stop.mark().side()));
    }

    @Test
    void theUnifiedViewFindsAContextLineByItsAnchorOnTheLeftToo() {
        Drawn unified = RichDiff.drawn(
                Expansion.Folded.of(ParsedDiff.of(DIFF)),
                Option.none(),
                Option.none(),
                RichDiff.Drawing.of(Theme.SpanStyles.NONE)
                        .withHighlights(HashSet.of(RichDiff.Highlight.WORDS))
                        .withWidth(AREA));

        assertTrue(unified.stops()
                .exists(stop -> stop.mark().is(new Anchor.OnLine("DiffStyle.java", DiffSide.OLD, 1))
                        && stop.mark().anchor().equals(new Anchor.OnLine("DiffStyle.java", DiffSide.NEW, 1))));
    }

    @Test
    void anAreaTooNarrowToSplitIsDrawnUnifiedAndSaysSo() {
        Drawn drawn = drawn(DIFF, RichDiff.SPLIT_WIDTH - 1);

        assertEquals(Option.of("too narrow to split, needs 100 columns"), drawn.note());
        assertEquals("1 1 │   enum DiffStyle {", text(drawn.lines()).get(1).stripTrailing());
        assertTrue(drawn.halves().isEmpty());
    }

    @Test
    void aRowAsWideAsTheAreaIsPinnedARowOfHalvesScrollsEachHalf() {
        Drawn drawn = drawn(DIFF, AREA);

        // The bar of the file has no halves, so it does not scroll sideways; a row of halves keeps no columns.
        assertEquals(Drawn.PINNED, drawn.keptAt(0));
        assertTrue(drawn.halvesOf(1).isDefined());
        assertEquals(0, drawn.keptAt(1));
    }

    @Test
    void scrolledSidewaysEachHalfKeepsItsGutterAndMarksTheCodeCutOff() {
        String longer =
                DIFF.replace("+    SPLIT", "+    SPLIT" + " is drawn with the old file beside the new one".repeat(2));

        List<String> rows = shown(drawn(longer, AREA), 4);

        // The code of each half from its fifth column on, a ‹ where code is cut off before it, a › after it.
        assertEquals(row("2 │ - ‹RICH", "2 │ + ‹RICH,"), rows.get(2).stripTrailing());
        assertTrue(rows.get(3).startsWith(" ".repeat(HALF) + "│3 │ + ‹SPLIT is drawn"));
        assertTrue(rows.get(3).endsWith("›"));
        assertEquals(AREA, rows.get(3).length());
    }

    @Test
    void aRowIsSearchedInTheCodeOfEachHalfAContextLineOnTheRightOnly() {
        Drawn drawn = drawn(DIFF, AREA);

        String changed = drawn.rows().source(2).text().replace('\0', '.');
        String context = drawn.rows().source(1).text().replace('\0', '.');

        assertTrue(changed.startsWith("    RICH...."), changed);
        assertTrue(changed.endsWith("    RICH,"), changed);
        assertTrue(context.startsWith(".".repeat("enum DiffStyle {".length())), context);
        assertTrue(context.endsWith("enum DiffStyle {"), context);
    }

    private static Drawn drawn(String diff, int width) {
        return RichDiff.drawn(
                Expansion.Folded.of(ParsedDiff.of(diff)),
                Option.none(),
                Option.none(),
                RichDiff.Drawing.of(Theme.SpanStyles.NONE)
                        .withHighlights(HashSet.of(RichDiff.Highlight.WORDS))
                        .withWidth(width)
                        .withSplit(RichDiff.Split.SIDE_BY_SIDE));
    }

    /**
     * The rows of {@code drawn} as the area shows them scrolled {@code column} columns sideways, past what comes
     * before the first file, but the markers of lines left out.
     */
    private static List<String> shown(Drawn drawn, int column) {
        return drawn.lines()
                .zipWithIndex()
                .map(row -> drawn.halvesOf(row._2())
                        .map(halves -> halves.shown(row._1(), column))
                        .getOrElse(row._1()))
                .map(line -> text(List.of(line)).head())
                .filter(row -> !row.startsWith("┄"));
    }

    /**
     * A row as it shows: {@code left} in the left half, then the rule, then {@code right}.
     */
    private static String row(String left, String right) {
        return (left + " ".repeat(HALF - left.length()) + "│" + right).stripTrailing();
    }

    private static List<String> text(List<Line> lines) {
        return lines.map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()));
    }
}
