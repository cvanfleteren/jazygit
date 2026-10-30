package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.state.Anchor.OnLine;
import net.vanfleteren.jazygit.state.DiffStops.HunkAt;
import org.junit.jupiter.api.Test;

/**
 * Where the cursor of a rich diff stops, and which stop it is on once its own is gone (ADR 60).
 */
class DiffStopsTest {

    // a.txt, its line 2 changed, in two hunks; then b.txt, binary.
    private static final UnifiedDiff DIFF = UnifiedDiff.parse("""
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
            """);

    private static final Anchor LEAD = new Anchor.Lead();
    private static final Anchor A = new Anchor.File("a.txt");
    private static final Anchor B = new Anchor.File("b.png");

    private static OnLine line(Side side, int number) {
        return new OnLine("a.txt", side, number);
    }

    private static ReviewThread thread(String id, Subject subject, Option<Integer> line) {
        return new ReviewThread(
                "a.txt",
                Side.RIGHT,
                line,
                Option.none(),
                Side.RIGHT,
                line,
                false,
                false,
                subject,
                List.of(new Comment("bob", Instant.EPOCH, "Hm", Option.none(), "C", "1")),
                0,
                id,
                ReviewThread.Rights.ALL);
    }

    @Test
    void theLeadThenEachFileItsBarItsLinesAndNoNote() {
        assertEquals(
                List.of(
                        LEAD,
                        A,
                        line(Side.RIGHT, 1),
                        line(Side.LEFT, 2),
                        line(Side.RIGHT, 2),
                        line(Side.RIGHT, 3),
                        line(Side.RIGHT, 10),
                        B),
                DiffStops.of(DIFF, List.empty(), true).anchors());
    }

    @Test
    void aLineKnowsItsHunk() {
        DiffStops stops = DiffStops.of(DIFF, List.empty(), true);

        assertEquals(Option.of(new HunkAt(0, 0)), stops.find(line(Side.LEFT, 2)).flatMap(DiffStops.Stop::hunk));
        assertEquals(
                Option.of(new HunkAt(0, 1)), stops.find(line(Side.RIGHT, 10)).flatMap(DiffStops.Stop::hunk));
        assertEquals(Option.none(), stops.find(A).flatMap(DiffStops.Stop::hunk));
    }

    @Test
    void eachThreadIsAStopWhereItIsDrawn() {
        ReviewThread onFile = thread("F", Subject.FILE, Option.none());
        ReviewThread onTwo = thread("L", Subject.LINE, Option.of(2));
        ReviewThread outdated = thread("O", Subject.LINE, Option.none());

        assertEquals(
                List.of(
                        LEAD,
                        A,
                        new Anchor.Thread("F", Option.none()),
                        line(Side.RIGHT, 1),
                        line(Side.LEFT, 2),
                        line(Side.RIGHT, 2),
                        new Anchor.Thread("L", Option.of(line(Side.RIGHT, 2))),
                        line(Side.RIGHT, 3),
                        line(Side.RIGHT, 10),
                        new Anchor.Thread("O", Option.none()),
                        B),
                DiffStops.of(DIFF, List.of(onFile, onTwo, outdated), true).anchors());
    }

    @Test
    void anAnchorThatIsAStopIsItsOwnStop() {
        List<Anchor> stops = DiffStops.of(DIFF, List.empty(), true).anchors();

        assertEquals(Option.of(line(Side.LEFT, 2)), DiffStops.nearest(stops, line(Side.LEFT, 2)));
        assertEquals(Option.of(B), DiffStops.nearest(stops, B));
    }

    @Test
    void aLineThatIsGoneGoesToTheClosestOfItsSideElseToItsFileElseToTheFirst() {
        List<Anchor> stops = DiffStops.of(DIFF, List.empty(), true).anchors();

        assertEquals(Option.of(line(Side.RIGHT, 10)), DiffStops.nearest(stops, line(Side.RIGHT, 8)));
        assertEquals(Option.of(line(Side.LEFT, 2)), DiffStops.nearest(stops, line(Side.LEFT, 40)));
        assertEquals(Option.of(B), DiffStops.nearest(stops, new OnLine("b.png", Side.RIGHT, 1)));
        assertEquals(Option.of(LEAD), DiffStops.nearest(stops, new OnLine("c.txt", Side.RIGHT, 1)));
        assertEquals(Option.of(LEAD), DiffStops.nearest(stops, new Anchor.File("c.txt")));
    }

    @Test
    void aThreadIsFoundByItsIdWhereverItIsElseItGoesToItsLine() {
        List<Anchor> stops = DiffStops.of(DIFF, List.of(thread("L", Subject.LINE, Option.of(3))), true)
                .anchors();

        assertEquals(
                Option.of(new Anchor.Thread("L", Option.of(line(Side.RIGHT, 3)))),
                DiffStops.nearest(stops, new Anchor.Thread("L", Option.of(line(Side.RIGHT, 2)))));
        assertEquals(
                Option.of(line(Side.RIGHT, 2)),
                DiffStops.nearest(stops, new Anchor.Thread("gone", Option.of(line(Side.RIGHT, 2)))));
        assertEquals(Option.of(LEAD), DiffStops.nearest(stops, new Anchor.Thread("gone", Option.none())));
    }

    @Test
    void withoutStopsTheCursorIsNowhere() {
        assertEquals(Option.none(), DiffStops.nearest(List.empty(), LEAD));
    }
}
