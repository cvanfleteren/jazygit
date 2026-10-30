package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffStops;
import org.junit.jupiter.api.Test;

/**
 * How the keys and the wheel move the cursor of a rich diff and scroll its view (ADR 60).
 */
class DiffCursorTest {

    private static final Anchor LEAD = new Anchor.Lead();
    private static final Anchor FILE = new Anchor.File("a.txt");
    private static final Anchor ONE = new Anchor.OnLine("a.txt", Side.RIGHT, 1);
    private static final Anchor THREAD = new Anchor.Thread("T", Option.of(new Anchor.OnLine("a.txt", Side.RIGHT, 1)));
    private static final Anchor TWO = new Anchor.OnLine("a.txt", Side.RIGHT, 2);
    private static final Anchor THREE = new Anchor.OnLine("a.txt", Side.RIGHT, 3);

    // 20 lines shown 5 at a time: a lead of 3 lines, a bar, line 1, a thread of 10 lines, line 2, and line 3 with the
    // 3 lines after it that are no stop.
    private static final Vector<RichDiff.Stop> STOPS = Vector.of(
            stop(LEAD, 3, 0), stop(FILE, 1, 3), line(ONE, 4), stop(THREAD, 10, 5), line(TWO, 15), line(THREE, 16));

    private static RichDiff.Stop stop(Anchor anchor, int lines, int row) {
        return new RichDiff.Stop(new RichDiff.Mark(anchor, lines, 8, Option.none()), row);
    }

    private static RichDiff.Stop line(Anchor anchor, int row) {
        return new RichDiff.Stop(new RichDiff.Mark(anchor, 1, 8, Option.of(new DiffStops.HunkAt(0, 0))), row);
    }

    private static DiffCursor on(Anchor anchor, int row) {
        return DiffCursor.of(STOPS, 20, 5, row, Option.of(anchor), Option.none())
                .get();
    }

    private static void assertAt(Anchor anchor, int row, DiffCursor cursor) {
        assertEquals(anchor, cursor.anchor());
        assertEquals(row, cursor.row());
    }

    @Test
    void itStartsOnTheFirstStopAndADiffWithoutStopsHasNone() {
        assertAt(
                LEAD,
                0,
                DiffCursor.of(STOPS, 20, 5, 0, Option.none(), Option.none()).get());
        assertEquals(Option.none(), DiffCursor.of(Vector.empty(), 20, 5, 0, Option.none(), Option.none()));
    }

    @Test
    void theViewIsScrolledToShowTheStopOfTheCursor() {
        assertAt(TWO, 11, on(TWO, 0));
        assertAt(ONE, 4, on(ONE, 15));
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
    void aMarkThatIsNotALineOfTheHunkOfTheCursorIsDropped() {
        assertEquals(
                Option.none(),
                DiffCursor.of(STOPS, 20, 5, 0, Option.of(TWO), Option.of(FILE))
                        .get()
                        .markAnchor());
        assertEquals(
                Option.of(ONE),
                DiffCursor.of(STOPS, 20, 5, 0, Option.of(TWO), Option.of(ONE))
                        .get()
                        .markAnchor());
    }

    @Test
    void theLinesOfARangeAreTintedAndMarkedInTheGutter() {
        Text text = Text.from(List.range(0, 20)
                .map(row -> Line.from(Span.raw(" 1  1 │ "), Span.raw("x" + row)))
                .toJavaList());
        Text tinted = on(TWO, 11).marked().get().up().tinted(text, Style.EMPTY.reversed());

        assertEquals(" 1  1 ┃ x4", tinted.lines().get(4).rawContent());
        assertEquals(" 1  1 ┃ x15", tinted.lines().get(15).rawContent());
        assertEquals(" 1  1 │ x5", tinted.lines().get(5).rawContent());
    }

    @Test
    void aBarOrALineIsTintedAsWideAsItsFileAndAThreadHasItsRuleReversed() {
        Text text = Text.from(List.range(0, 20)
                .map(row ->
                        row == 5 ? Line.from(Span.raw("  "), Span.raw("┏━"), Span.raw(" bob")) : Line.from("x" + row))
                .toJavaList());
        Style tint = Style.EMPTY.reversed();

        Line bar = on(FILE, 0).tinted(text, tint).lines().get(3);
        assertEquals("x3      ", bar.rawContent());
        assertTrue(bar.spans().stream()
                .allMatch(span -> span.style().addModifiers().contains(Modifier.REVERSED)));

        Line rule = on(THREAD, 5).tinted(text, tint).lines().get(5);
        assertTrue(rule.spans().get(1).style().addModifiers().contains(Modifier.REVERSED));
        assertFalse(rule.spans().get(0).style().addModifiers().contains(Modifier.REVERSED));

        assertEquals(text, on(LEAD, 0).tinted(text, tint));
    }
}
