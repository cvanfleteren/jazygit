package net.vanfleteren.jazygit.content.diff;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import org.junit.jupiter.api.Test;

/**
 * Tests on where {@code :} with a line number goes (ADR 140).
 */
class LineGoalTest {

    private static final DiffStops.HunkAt HUNK = new DiffStops.HunkAt(0, 0);

    // a.txt: lines 1 to 9 left out, a hunk of context 10, removed old 11, added new 11, context 12, then more left
    // out; b.txt: an added line 1.
    private static final Vector<Drawn.Stop> DIFF = Vector.of(
            stop(new Anchor.Lead(), 0),
            stop(new Anchor.File("a.txt"), 1),
            stop(new Anchor.Gap("a.txt", 1, Option.some(10)), 2),
            context("a.txt", 10, 10, 3),
            stop(new Anchor.OnLine("a.txt", Side.LEFT, 11), 4),
            stop(new Anchor.OnLine("a.txt", Side.RIGHT, 11), 5),
            context("a.txt", 12, 12, 6),
            stop(new Anchor.Gap("a.txt", 13, Option.none()), 7),
            stop(new Anchor.File("b.txt"), 8),
            stop(new Anchor.OnLine("b.txt", Side.RIGHT, 1), 9));

    @Test
    void aLineShownIsTheStopOfItsLineInTheNewFile() {
        assertEquals(new LineGoal.OnStop(5, true, 11), LineGoal.of(DIFF, Vector.empty(), 3, Side.RIGHT, 11));
        assertEquals(new LineGoal.OnStop(6, true, 12), LineGoal.of(DIFF, Vector.empty(), 3, Side.RIGHT, 12));
    }

    @Test
    void onTheOldSideALineIsNumberedInTheOldFileAContextLineByItsOtherAnchor() {
        assertEquals(new LineGoal.OnStop(4, true, 11), LineGoal.of(DIFF, Vector.empty(), 4, Side.LEFT, 11));
        assertEquals(new LineGoal.OnStop(3, true, 10), LineGoal.of(DIFF, Vector.empty(), 4, Side.LEFT, 10));
    }

    @Test
    void aLineOfTheNewFileAMarkerLeavesOutShowsTheLinesAroundItWithinTheMarker() {
        assertEquals(
                new LineGoal.Hidden(
                        new Anchor.OnLine("a.txt", Side.RIGHT, 3), new Anchor.Gap("a.txt", 1, Option.some(9)), 3),
                LineGoal.of(DIFF, Vector.empty(), 1, Side.RIGHT, 3));
        // After the last hunk, how many lines follow is not known.
        assertEquals(
                new LineGoal.Hidden(
                        new Anchor.OnLine("a.txt", Side.RIGHT, 40), new Anchor.Gap("a.txt", 35, Option.some(46)), 6),
                LineGoal.of(DIFF, Vector.empty(), 6, Side.RIGHT, 40));
    }

    @Test
    void aLinePastTheEndOfAFileWhoseLengthIsKnownIsTheClosestLineShown() {
        // The file read whole: the marker after the last hunk knows the file ends at line 30.
        Vector<Drawn.Stop> read = DIFF.update(7, stop(new Anchor.Gap("a.txt", 13, Option.some(31)), 7));

        assertEquals(new LineGoal.OnStop(6, false, 12), LineGoal.of(read, Vector.empty(), 6, Side.RIGHT, 99));
        assertInstanceOf(LineGoal.Hidden.class, LineGoal.of(read, Vector.empty(), 6, Side.RIGHT, 30));
    }

    @Test
    void aLineOfTheOldFileAMarkerLeavesOutIsTheClosestLineShown() {
        assertEquals(new LineGoal.OnStop(3, false, 10), LineGoal.of(DIFF, Vector.empty(), 4, Side.LEFT, 3));
    }

    @Test
    void theNumberIsOfTheFileOfTheCursorTheFirstOneOnTheLead() {
        assertEquals(new LineGoal.OnStop(9, true, 1), LineGoal.of(DIFF, Vector.empty(), 9, Side.RIGHT, 1));
        assertEquals(new LineGoal.OnStop(9, false, 1), LineGoal.of(DIFF, Vector.empty(), 8, Side.RIGHT, 7));
        assertInstanceOf(LineGoal.Hidden.class, LineGoal.of(DIFF, Vector.empty(), 0, Side.RIGHT, 1));
    }

    @Test
    void aFileWithoutLinesOnTheSideAskedIsNumberedOnItsOtherSide() {
        Vector<Drawn.Stop> deleted = Vector.of(
                stop(new Anchor.File("gone.txt"), 0),
                stop(new Anchor.OnLine("gone.txt", Side.LEFT, 1), 1),
                stop(new Anchor.OnLine("gone.txt", Side.LEFT, 2), 2));

        assertEquals(new LineGoal.OnStop(2, true, 2), LineGoal.of(deleted, Vector.empty(), 0, Side.RIGHT, 2));
        assertEquals(new LineGoal.OnStop(2, false, 2), LineGoal.of(deleted, Vector.empty(), 0, Side.RIGHT, 9));
    }

    @Test
    void aVersionRenderedGoesToTheRowOfItsLineElseOfTheLastLineBeforeItDrawn() {
        Vector<Drawn.Stop> bar = Vector.of(stop(new Anchor.File("README.md"), 0));
        Vector<Drawn.Place> places =
                Vector.of(place(1, 1), place(2, 1), place(3, 4), place(4, 4), place(9, 7), place(10, 7));

        assertEquals(new LineGoal.InRow(4, true, 4), LineGoal.of(bar, places, 0, Side.RIGHT, 4));
        // As one in a closed node, which draws only its first line.
        assertEquals(new LineGoal.InRow(4, false, 4), LineGoal.of(bar, places, 0, Side.RIGHT, 6));
        assertEquals(new LineGoal.InRow(7, false, 10), LineGoal.of(bar, places, 0, Side.RIGHT, 99));
        assertEquals(new LineGoal.InRow(1, false, 1), LineGoal.of(bar, places, 0, Side.RIGHT, 0));
    }

    @Test
    void whatHasNoLinesOfAFileHasNowhereToGo() {
        Vector<Drawn.Stop> binary = Vector.of(stop(new Anchor.Lead(), 0), stop(new Anchor.File("logo.png"), 1));

        assertInstanceOf(LineGoal.Nowhere.class, LineGoal.of(binary, Vector.empty(), 1, Side.RIGHT, 1));
        assertInstanceOf(LineGoal.Nowhere.class, LineGoal.of(Vector.empty(), Vector.empty(), 0, Side.RIGHT, 1));
        assertFalse(LineGoal.numbered(binary, Vector.empty(), 1));
        assertTrue(LineGoal.numbered(DIFF, Vector.empty(), 0));
    }

    private static Drawn.Stop stop(Anchor anchor, int row) {
        return new Drawn.Stop(new Drawn.Mark(anchor, 1, 10, Option.some(HUNK)), row);
    }

    /**
     * A context line, by its number in the new file, and in the old one as its other anchor.
     */
    private static Drawn.Stop context(String path, int number, int old, int row) {
        return new Drawn.Stop(
                new Drawn.Mark(
                        new Anchor.OnLine(path, Side.RIGHT, number),
                        1,
                        10,
                        Option.some(HUNK),
                        Option.none(),
                        0,
                        Option.some(new Anchor.OnLine(path, Side.LEFT, old)),
                        0),
                row);
    }

    private static Drawn.Place place(int line, int row) {
        return new Drawn.Place(new Anchor.OnLine("README.md", Side.RIGHT, line), row);
    }
}
