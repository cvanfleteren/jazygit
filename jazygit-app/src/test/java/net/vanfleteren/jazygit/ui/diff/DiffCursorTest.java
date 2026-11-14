package net.vanfleteren.jazygit.ui.diff;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.ui.content.ContentText;
import net.vanfleteren.jazygit.ui.text.Rows;
import org.junit.jupiter.api.Test;

/**
 * How the keys and the wheel move the cursor of a rich diff and scroll its view (ADR 60).
 */
class DiffCursorTest {

    private static final Anchor LEAD = new Anchor.Lead();
    private static final Anchor FILE = new Anchor.File("a.txt");
    private static final Anchor ONE = new Anchor.OnLine("a.txt", Side.RIGHT, 1);
    private static final Anchor THREAD =
            new Anchor.Thread(new ReviewThread.Id("T"), Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 1)));
    private static final Anchor TWO = new Anchor.OnLine("a.txt", Side.RIGHT, 2);
    private static final Anchor THREE = new Anchor.OnLine("a.txt", Side.RIGHT, 3);

    // 20 lines shown 5 at a time: a lead of 3 lines, a bar, line 1, a thread of 10 lines, line 2, and line 3 with the
    // 3 lines after it that are no stop.
    private static final Vector<RichDiff.Stop> STOPS = Vector.of(
            stop(LEAD, 3, 0), stop(FILE, 1, 3), line(ONE, 4), stop(THREAD, 10, 5), line(TWO, 15), line(THREE, 16));

    private static final Anchor FIRST =
            new Anchor.Thread(new ReviewThread.Id("T1"), Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 1)));
    private static final Anchor LAST =
            new Anchor.Thread(new ReviewThread.Id("T2"), Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 2)));

    // A bar, line 1 and its thread, line 2 and its thread, and line 3: 6 lines, all on one screen.
    private static final Vector<RichDiff.Stop> TWO_THREADS = Vector.of(
            stop(FILE, 1, 0), line(ONE, 1), stop(FIRST, 1, 2), line(TWO, 3), stop(LAST, 1, 4), line(THREE, 5));

    private static RichDiff.Stop stop(Anchor anchor, int lines, int row) {
        return new RichDiff.Stop(new RichDiff.Mark(anchor, lines, 8, Option.none()), row);
    }

    private static RichDiff.Stop line(Anchor anchor, int row) {
        return new RichDiff.Stop(new RichDiff.Mark(anchor, 1, 8, Option.of(new DiffStops.HunkAt(0, 0))), row);
    }

    /**
     * The cursor of a diff of {@code lines} lines with {@code stops}, looked for from the first stop.
     */
    private static Option<DiffCursor> cursor(
            Vector<RichDiff.Stop> stops, int lines, int height, int row, Option<Anchor> at, Option<Anchor> mark) {
        return DiffCursor.of(stops, 0, lines, height, row, at, mark);
    }

    private static DiffCursor on(Anchor anchor, int row) {
        return cursor(STOPS, 20, 5, row, Option.of(anchor), Option.none()).get();
    }

    private static void assertAt(Anchor anchor, int row, DiffCursor cursor) {
        assertEquals(anchor, cursor.anchor());
        assertEquals(row, cursor.row());
    }

    @Test
    void itStartsOnTheFirstStopAndADiffWithoutStopsHasNone() {
        assertAt(LEAD, 0, cursor(STOPS, 20, 5, 0, Option.none(), Option.none()).get());
        assertEquals(Option.none(), cursor(Vector.empty(), 20, 5, 0, Option.none(), Option.none()));
    }

    @Test
    void theViewIsScrolledToShowTheStopOfTheCursor() {
        assertAt(TWO, 11, on(TWO, 0));
        assertAt(ONE, 4, on(ONE, 15));
    }

    @Test
    void aMatchOfASearchTakesTheCursorToItsStopAndTheViewToItsLine() {
        // As much of the thread as fits, then on to the line of the match in it.
        assertAt(THREAD, 8, on(LEAD, 0).toLine(12).get());
        assertAt(THREE, 15, on(LEAD, 0).toLine(16).get());
        assertEquals(16, on(LEAD, 0).toLine(16).get().stopStart());
        assertEquals(20, on(LEAD, 0).toLine(16).get().stopEnd());
    }

    @Test
    void whileARangeIsOpenAMatchOnlyReachesItToALineOfItsHunk() {
        DiffCursor ranged =
                cursor(STOPS, 20, 5, 0, Option.of(ONE), Option.of(ONE)).get();

        assertEquals(Option.none(), ranged.toLine(12));
        DiffCursor reached = ranged.toLine(15).get();
        assertEquals(TWO, reached.anchor());
        assertEquals(Option.of(ONE), reached.markAnchor());
    }

    @Test
    void downMovesFromStopToStopAndTheViewFollows() {
        DiffCursor cursor = on(LEAD, 0).down();
        assertAt(FILE, 0, cursor);
        cursor = cursor.down();
        assertAt(ONE, 0, cursor);
    }

    @Test
    void aStopHigherThanTheViewIsScrolledThroughBeforeTheNextOne() {
        DiffCursor thread = on(ONE, 0).down();
        // As much of the thread as fits, from its first line.
        assertAt(THREAD, 5, thread);

        DiffCursor through = thread.down().down().down().down().down();
        assertAt(THREAD, 10, through);

        assertAt(TWO, 11, through.down());
    }

    @Test
    void upScrollsThroughTheLinesOfItsStopAboveTheScreenFirst() {
        DiffCursor thread = on(TWO, 11).up();
        // As much of the thread as fits, to its last line.
        assertAt(THREAD, 10, thread);

        DiffCursor top = thread.up().up().up().up().up();
        assertAt(THREAD, 5, top);
        assertAt(ONE, 4, top.up());
    }

    @Test
    void theLeadIsScrolledThroughToTheTop() {
        DiffCursor lead = on(FILE, 3).up();
        assertAt(LEAD, 0, lead);
        assertAt(LEAD, 0, lead.up());
    }

    @Test
    void theLastStopScrollsToTheEndOfTheDiff() {
        DiffCursor last = on(THREE, 11);
        assertAt(THREE, 15, last.down().down().down().down().down());
    }

    @Test
    void homeAndEndGoToTheFirstAndTheLastStop() {
        assertAt(LEAD, 0, on(TWO, 11).home());
        assertAt(THREE, 15, on(LEAD, 0).end());
    }

    @Test
    void aScrollThatWouldLeaveTheCursorOffTheScreenPullsItAlong() {
        assertAt(THREAD, 6, on(ONE, 4).scrolled(2));
        assertAt(THREAD, 8, on(TWO, 11).scrolled(-3));
        // While it has a line on the screen, it stays.
        assertAt(THREAD, 7, on(THREAD, 5).scrolled(2));
    }

    @Test
    void pagesMoveTheViewAndPullTheCursorToTheFirstOrLastStopOnTheScreen() {
        assertAt(FILE, 3, on(LEAD, 0).scrolled(3));
        assertAt(THREAD, 5, on(LEAD, 0).pageDown());
        assertAt(THREAD, 10, on(THREE, 15).pageUp());
    }

    @Test
    void aRangeStartsOnALineAndMovesOverTheLinesOfItsHunkOnly() {
        assertEquals(Option.none(), on(FILE, 0).marked());
        DiffCursor range = on(TWO, 11).marked().get();
        assertEquals(Option.of(TWO), range.markAnchor());

        assertAt(THREE, 15, range.down());
        assertEquals(THREE, range.down().down().anchor());
        // Over the thread between two lines, and no further than the first line of the hunk.
        assertAt(ONE, 4, range.up());
        assertEquals(ONE, range.up().up().anchor());
        assertEquals(Option.none(), range.nextThread());
        assertEquals(TWO, range.home().anchor());

        assertEquals(Option.none(), range.marked().get().markAnchor());
    }

    @Test
    void theNextAndThePreviousThread() {
        assertAt(THREAD, 5, on(LEAD, 0).nextThread().get());
        assertAt(THREAD, 10, on(THREE, 15).previousThread().get());
        // Around the end, and the start; the view stays where the thread is shown already.
        assertAt(THREAD, 11, on(TWO, 11).nextThread().get());
        assertAt(THREAD, 5, on(LEAD, 0).previousThread().get());
        // The only thread is no other.
        assertEquals(Option.none(), on(THREAD, 5).nextThread());
        assertEquals(Option.none(), on(THREAD, 5).previousThread());
    }

    @Test
    void theNextAndThePreviousThreadGoAroundInTheirOwnDirection() {
        assertEquals(FIRST, onTwoThreads(THREE).nextThread().get().anchor());
        assertEquals(LAST, onTwoThreads(FILE).previousThread().get().anchor());
        assertEquals(LAST, onTwoThreads(TWO).nextThread().get().anchor());
        assertEquals(FIRST, onTwoThreads(TWO).previousThread().get().anchor());
    }

    @Test
    void aCommentIsAStopThatTheNextAndThePreviousThreadGoFrom() {
        Anchor reply = new Anchor.Comment(new ReviewThread.Comment.Id("R"), (Anchor.Thread) FIRST);
        Vector<RichDiff.Stop> stops = Vector.of(
                stop(FILE, 1, 0),
                line(ONE, 1),
                stop(FIRST, 1, 2),
                stop(reply, 1, 3),
                line(TWO, 4),
                stop(LAST, 1, 5),
                line(THREE, 6));
        DiffCursor onReply =
                cursor(stops, 7, 20, 0, Option.of(reply), Option.none()).get();

        // n and N go from thread to thread, not from comment to comment (ADR 62).
        assertEquals(LAST, onReply.nextThread().get().anchor());
        assertEquals(FIRST, onReply.previousThread().get().anchor());
        assertEquals(reply, onReply.up().down().anchor());
        assertEquals(TWO, onReply.down().anchor());
        assertEquals(FIRST, onReply.up().anchor());
    }

    private static DiffCursor onTwoThreads(Anchor anchor) {
        return cursor(TWO_THREADS, 6, 20, 0, Option.of(anchor), Option.none()).get();
    }

    @Test
    void aClickPutsTheCursorOnTheStopOfTheLineClickedAndLeavesTheView() {
        // The view from line 3 shows the bar, line 1 and the first 3 lines of the thread.
        assertAt(FILE, 3, on(FILE, 3).clicked(0).get());
        assertAt(ONE, 3, on(FILE, 3).clicked(1).get());
        assertAt(THREAD, 3, on(FILE, 3).clicked(4).get());
        // The 3 lines after line 3 are its.
        assertAt(THREE, 15, on(TWO, 15).clicked(4).get());

        assertEquals(Option.none(), on(FILE, 3).clicked(-1));
        assertEquals(Option.none(), on(FILE, 3).clicked(5));
    }

    @Test
    void whileARangeIsOpenAClickReachesItOnlyToALineOfItsHunk() {
        // The view from line 12 shows the end of the thread, line 2 and line 3.
        DiffCursor range = on(TWO, 12).marked().get();

        assertEquals(Option.none(), range.clicked(0));
        DiffCursor reached = range.clicked(4).get();
        assertEquals(THREE, reached.anchor());
        assertEquals(Option.of(TWO), reached.markAnchor());
    }

    @Test
    void theCursorOnAnAnchorThatIsTwiceIsOnTheOneNearestWhereItWas() {
        Vector<RichDiff.Stop> stops = Vector.of(stop(FILE, 1, 0), line(ONE, 1), line(FILE, 2));

        assertEquals(
                2,
                DiffCursor.of(stops, 2, 3, 5, 0, Option.of(FILE), Option.none())
                        .get()
                        .stop());
        assertEquals(
                0,
                DiffCursor.of(stops, 0, 3, 5, 0, Option.of(FILE), Option.none())
                        .get()
                        .stop());
    }

    @Test
    void aMarkThatIsInTwoHunksIsTheOneInTheHunkOfTheCursor() {
        // Line 1 of the first hunk, and line 1 again in a second hunk far from it, as the cursor is.
        RichDiff.Stop other = new RichDiff.Stop(new RichDiff.Mark(ONE, 1, 8, Option.of(new DiffStops.HunkAt(0, 1))), 2);
        RichDiff.Stop cursorOn =
                new RichDiff.Stop(new RichDiff.Mark(TWO, 1, 8, Option.of(new DiffStops.HunkAt(0, 1))), 3);
        Vector<RichDiff.Stop> stops = Vector.of(line(ONE, 0), stop(FILE, 1, 1), other, cursorOn);

        DiffCursor cursor =
                DiffCursor.of(stops, 0, 4, 5, 0, Option.of(TWO), Option.of(ONE)).get();

        assertEquals(Option.of(2), cursor.mark());
    }

    @Test
    void aMarkThatIsNotALineOfTheHunkOfTheCursorIsDropped() {
        assertEquals(
                Option.none(),
                cursor(STOPS, 20, 5, 0, Option.of(TWO), Option.of(FILE)).get().markAnchor());
        assertEquals(
                Option.of(ONE),
                cursor(STOPS, 20, 5, 0, Option.of(TWO), Option.of(ONE)).get().markAnchor());
    }

    @Test
    void theStopOfAnAnchorIsTheOneWithItClosestToWhereItIsLookedForFrom() {
        Vector<RichDiff.Stop> stops = Vector.of(stop(FILE, 1, 0), line(ONE, 1), line(TWO, 2), line(FILE, 3));

        assertEquals(Option.of(0), DiffCursor.index(stops, FILE, 0));
        assertEquals(Option.of(3), DiffCursor.index(stops, FILE, 2));
        assertEquals(Option.of(1), DiffCursor.index(stops, ONE, 3));
        // As far before as after, the one before.
        assertEquals(Option.of(1), DiffCursor.index(stops, ONE, 99));
        assertEquals(Option.of(0), DiffCursor.index(Vector.of(stop(FILE, 1, 0), line(ONE, 1), line(FILE, 2)), FILE, 1));
        assertEquals(Option.none(), DiffCursor.index(stops, THREE, 1));
        assertEquals(Option.none(), DiffCursor.index(Vector.empty(), FILE, 0));
    }

    @Test
    void theCursorIsOnTheStopOfItsAnchorOrTheNearest() {
        // Line 4 is no stop: the nearest of its side of the file is line 3.
        assertAt(
                THREE,
                15,
                cursor(STOPS, 20, 5, 15, Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 4)), Option.none())
                        .get());
        assertAt(THREAD, 5, on(THREAD, 5));
    }

    @Test
    void aLineAsWideAsItsFileIsTintedWithPaddingTheAreaDrawsToItsEdge() {
        // Line 1 is a context line as wide as its file: it ends in its text, not in padding.
        Rows text = Rows.of(Vector.range(0, 20).map(row -> Line.from(Span.raw(" 1 │ "), Span.raw("abc"))));
        Style tint = Style.EMPTY.reversed();

        Line tinted = on(ONE, 4).tinted(text, tint).get(4);

        assertEquals("", List.ofAll(tinted.spans()).last().content());
        Line filled = ContentText.filled(tinted, 12);
        assertEquals(12, filled.width());
        assertEquals(new Span("    ", tint), List.ofAll(filled.spans()).last());
    }

    @Test
    void theLinesOfARangeAreTintedAndMarkedInTheGutter() {
        Rows text = Rows.of(Vector.range(0, 20).map(row -> Line.from(Span.raw(" 1  1 │ "), Span.raw("x" + row))));
        Rows tinted = on(TWO, 11).marked().get().up().tinted(text, Style.EMPTY.reversed());

        assertEquals(" 1  1 ┃ x4", tinted.get(4).rawContent());
        assertEquals(" 1  1 ┃ x15", tinted.get(15).rawContent());
        assertEquals(" 1  1 │ x5", tinted.get(5).rawContent());
    }

    @Test
    void aBarOrALineIsTintedAsWideAsItsFileAndAThreadHasItsRuleReversed() {
        Rows text = Rows.of(Vector.range(0, 20).map(row -> switch (row) {
            case 5 -> Line.from(Span.raw("  "), Span.raw("┏━"), Span.raw(" bob"));
            case 6 -> Line.from(Span.raw("  "), Span.raw("┃ "), Span.raw("me no like"));
            default -> Line.from("x" + row);
        }));
        Style tint = Style.EMPTY.reversed();

        Line bar = on(FILE, 0).tinted(text, tint).get(3);
        assertEquals("x3      ", bar.rawContent());
        assertTrue(bar.spans().stream()
                .allMatch(span -> span.style().addModifiers().contains(Modifier.REVERSED)));

        Line rule = on(THREAD, 5).tinted(text, tint).get(5);
        assertTrue(rule.spans().get(1).style().addModifiers().contains(Modifier.REVERSED));
        assertFalse(rule.spans().get(0).style().addModifiers().contains(Modifier.REVERSED));
        // Only the stroke, not the space after it.
        Line body = on(THREAD, 5).tinted(text, tint).get(6);
        assertEquals("  ┃ me no like", body.rawContent());
        assertEquals(
                List.of(false, true, false, false),
                List.ofAll(body.spans()).map(span -> span.style().addModifiers().contains(Modifier.REVERSED)));

        assertEquals(text, on(LEAD, 0).tinted(text, tint));
    }
}
