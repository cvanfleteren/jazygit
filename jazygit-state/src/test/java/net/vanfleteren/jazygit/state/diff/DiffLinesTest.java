package net.vanfleteren.jazygit.state.diff;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.state.diff.Anchor.OnLine;
import net.vanfleteren.jazygit.state.diff.DiffStops.HunkAt;
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

    private static OnLine line(Side side, int number) {
        return new OnLine("a.txt", side, number);
    }

    @Test
    void eachLineOfEachHunkInOrderWithItsHunkAndNoNote() {
        assertEquals(
                List.of(
                        line(Side.RIGHT, 1),
                        line(Side.LEFT, 2),
                        line(Side.RIGHT, 2),
                        line(Side.RIGHT, 3),
                        line(Side.RIGHT, 10)),
                LINES.lines().map(DiffLines.Line::anchor).toList());
        assertEquals(
                List.of(new HunkAt(0, 0), new HunkAt(0, 0), new HunkAt(0, 0), new HunkAt(0, 0), new HunkAt(0, 1)),
                LINES.lines().map(DiffLines.Line::hunk).toList());
        assertEquals("ten", LINES.lines().last().line().text());
    }

    @Test
    void aRangeRunsInTheOrderOfTheDiffWhicheverEndComesFirst() {
        List<OnLine> range = List.of(line(Side.LEFT, 2), line(Side.RIGHT, 2), line(Side.RIGHT, 3));

        assertEquals(
                Option.of(range),
                LINES.between(line(Side.LEFT, 2), line(Side.RIGHT, 3))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
        assertEquals(
                Option.of(range),
                LINES.between(line(Side.RIGHT, 3), line(Side.LEFT, 2))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
        assertEquals(
                Option.of(List.of(line(Side.RIGHT, 10))),
                LINES.between(line(Side.RIGHT, 10), line(Side.RIGHT, 10))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
    }

    @Test
    void aContextLineIsFoundByItsNumberInTheOldFileOnTheLeftAndKeepsThatAnchor() {
        assertEquals(
                Option.of(List.of(line(Side.LEFT, 1), line(Side.LEFT, 2))),
                LINES.between(line(Side.LEFT, 1), line(Side.LEFT, 2))
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
    }

    @Test
    void inTheSplitViewARangeOnOneSideHoldsTheLinesOfThatSideOnly() {
        // From line 1 to line 3 of the new file: the removed line between them is on the other side.
        assertEquals(
                Option.of(List.of("one", "TWO", "three")),
                LINES.between(line(Side.RIGHT, 1), line(Side.RIGHT, 3), true)
                        .map(lines -> lines.map(held -> held.line().text()).toList()));
        // On the left, the context lines by their numbers in the old file.
        assertEquals(
                Option.of(List.of(line(Side.LEFT, 1), line(Side.LEFT, 2), line(Side.LEFT, 3))),
                LINES.between(line(Side.LEFT, 1), line(Side.LEFT, 3), true)
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
        // The unified view holds every line between them, as it shows them.
        assertEquals(
                Option.of(List.of("one", "two", "TWO", "three")),
                LINES.between(line(Side.RIGHT, 1), line(Side.RIGHT, 3), false)
                        .map(lines -> lines.map(held -> held.line().text()).toList()));
    }

    @Test
    void inTheSplitViewARangeFromOneSideToTheOtherHoldsEveryLineBetween() {
        assertEquals(
                Option.of(List.of(line(Side.LEFT, 1), line(Side.LEFT, 2), line(Side.RIGHT, 2))),
                LINES.between(line(Side.LEFT, 1), line(Side.RIGHT, 2), true)
                        .map(lines -> lines.map(DiffLines.Line::anchor).toList()));
    }

    @Test
    void noRangeUnlessBothEndsAreLines() {
        assertEquals(Option.none(), LINES.between(line(Side.LEFT, 2), line(Side.LEFT, 40)));
        assertEquals(Option.none(), LINES.between(new OnLine("b.png", Side.RIGHT, 1), line(Side.LEFT, 2)));
        assertEquals(Option.none(), new DiffLines(Vector.empty()).between(line(Side.LEFT, 2), line(Side.LEFT, 2)));
    }
}
