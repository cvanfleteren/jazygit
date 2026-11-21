package net.vanfleteren.jazygit.kernel.state.diff;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor.OnLine;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops.HunkAt;
import org.junit.jupiter.api.Test;

/**
 * The lines of a diff the cursor of the rich view stops on, and a range of them (ADR 60).
 */
class DiffLinesTest {

    // a.txt, its line 2 changed, in two hunks; then b.txt, binary.
    private static final DiffLines LINES = DiffLines.of(UnifiedDiff.parse("""
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            @@ -10,1 +10,1 @@
             ten
            \\ No newline at end of file
            diff --git a/b.png b/b.png
            Binary files a/b.png and b/b.png differ
            """));

    private static OnLine line(DiffSide side, int number) {
        return new OnLine("a.txt", side, number);
    }

    @Test
    void eachLineOfEachHunkInOrderWithItsHunkAndNoNote() {
        assertEquals(
                List.of(
                        line(DiffSide.NEW, 1),
                        line(DiffSide.OLD, 2),
                        line(DiffSide.NEW, 2),
                        line(DiffSide.NEW, 3),
                        line(DiffSide.NEW, 10)),
                LINES.lines().map(DiffLines.Line::anchor).toList());
        assertEquals(
                List.of(new HunkAt(0, 0), new HunkAt(0, 0), new HunkAt(0, 0), new HunkAt(0, 0), new HunkAt(0, 1)),
                LINES.lines().map(DiffLines.Line::hunk).toList());
        assertEquals("ten", LINES.lines().last().line().text());
    }

    @Test
    void aRangeRunsInTheOrderOfTheDiffWhicheverEndComesFirst() {
        List<OnLine> range = List.of(line(DiffSide.OLD, 2), line(DiffSide.NEW, 2), line(DiffSide.NEW, 3));

        assertEquals(
                Option.of(range),
                LINES.between(line(DiffSide.OLD, 2), line(DiffSide.NEW, 3))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
        assertEquals(
                Option.of(range),
                LINES.between(line(DiffSide.NEW, 3), line(DiffSide.OLD, 2))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
        assertEquals(
                Option.of(List.of(line(DiffSide.NEW, 10))),
                LINES.between(line(DiffSide.NEW, 10), line(DiffSide.NEW, 10))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
    }

    @Test
    void aContextLineIsFoundByItsNumberInTheOldFileOnTheLeftAndKeepsThatAnchor() {
        assertEquals(
                Option.of(List.of(line(DiffSide.OLD, 1), line(DiffSide.OLD, 2))),
                LINES.between(line(DiffSide.OLD, 1), line(DiffSide.OLD, 2))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
    }

    @Test
    void inTheSplitViewARangeOnOneSideHoldsTheLinesOfThatSideOnly() {
        // From line 1 to line 3 of the new file: the removed line between them is on the other side.
        assertEquals(
                Option.of(List.of("one", "TWO", "three")),
                LINES.between(line(DiffSide.NEW, 1), line(DiffSide.NEW, 3), true)
                        .map(lines -> lines.map(held -> held.line().text()).toList()));
        // On the left, the context lines by their numbers in the old file.
        assertEquals(
                Option.of(List.of(line(DiffSide.OLD, 1), line(DiffSide.OLD, 2), line(DiffSide.OLD, 3))),
                LINES.between(line(DiffSide.OLD, 1), line(DiffSide.OLD, 3), true)
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
        // The unified view holds every line between them, as it shows them.
        assertEquals(
                Option.of(List.of("one", "two", "TWO", "three")),
                LINES.between(line(DiffSide.NEW, 1), line(DiffSide.NEW, 3), false)
                        .map(lines -> lines.map(held -> held.line().text()).toList()));
    }

    @Test
    void noRangeUnlessBothEndsAreLines() {
        assertEquals(Option.none(), LINES.between(line(DiffSide.OLD, 2), line(DiffSide.OLD, 40)));
        assertEquals(Option.none(), LINES.between(new OnLine("b.png", DiffSide.NEW, 1), line(DiffSide.OLD, 2)));
        assertEquals(
                Option.none(), new DiffLines(Vector.empty()).between(line(DiffSide.OLD, 2), line(DiffSide.OLD, 2)));
    }
}
