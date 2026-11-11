package net.vanfleteren.jazygit.state.diff;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import net.vanfleteren.jazygit.state.diff.Anchor.OnLine;
import org.junit.jupiter.api.Test;

/**
 * Where the cursor of a rich diff stops, and which stop it is on once its own is gone (ADR 60).
 */
class DiffStopsTest {

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
                List.of(new Comment(
                        "bob",
                        Instant.EPOCH,
                        "Hm",
                        Option.none(),
                        new ReviewThread.Comment.Id("C"),
                        new ReviewThread.Comment.RestId("1"),
                        CommentRights.ALL)),
                0,
                new ReviewThread.Id(id),
                ReviewThread.Rights.ALL);
    }

    // The stops of a diff without threads, as the rich view draws them: a.txt, its line 2 changed, in two hunks; then
    // b.txt, binary.
    private static final List<Anchor> STOPS = List.of(
            LEAD,
            A,
            line(Side.RIGHT, 1),
            line(Side.LEFT, 2),
            line(Side.RIGHT, 2),
            line(Side.RIGHT, 3),
            line(Side.RIGHT, 10),
            B);

    @Test
    void anAnchorThatIsAStopIsItsOwnStop() {
        assertEquals(Option.of(line(Side.LEFT, 2)), DiffStops.nearest(STOPS, line(Side.LEFT, 2)));
        assertEquals(Option.of(B), DiffStops.nearest(STOPS, B));
    }

    @Test
    void aMarkerThatIsGoneGoesToTheMarkerOfItsFileThatStartsClosestElseToTheLineWhereItStarted() {
        Anchor.Gap above = new Anchor.Gap("a.txt", 4, Option.of(10));
        Anchor.Gap after = new Anchor.Gap("a.txt", 11, Option.none());
        List<Anchor> stops = List.of(A, line(Side.RIGHT, 3), above, line(Side.RIGHT, 10), after, B);

        // Expanding ten lines down from the hunk above moves where the marker starts.
        assertEquals(Option.of(above), DiffStops.nearest(stops, new Anchor.Gap("a.txt", 1, Option.of(10))));
        assertEquals(Option.of(after), DiffStops.nearest(stops, new Anchor.Gap("a.txt", 14, Option.none())));
        // Expanding all of it leaves no marker: the cursor goes to the line where it started.
        assertEquals(
                Option.of(line(Side.RIGHT, 10)), DiffStops.nearest(STOPS, new Anchor.Gap("a.txt", 8, Option.of(10))));
    }

    @Test
    void aLineThatIsGoneGoesToTheClosestOfItsSideElseToItsFileElseToTheFirst() {
        assertEquals(Option.of(line(Side.RIGHT, 10)), DiffStops.nearest(STOPS, line(Side.RIGHT, 8)));
        assertEquals(Option.of(line(Side.LEFT, 2)), DiffStops.nearest(STOPS, line(Side.LEFT, 40)));
        assertEquals(Option.of(B), DiffStops.nearest(STOPS, new OnLine("b.png", Side.RIGHT, 1)));
        assertEquals(Option.of(LEAD), DiffStops.nearest(STOPS, new OnLine("c.txt", Side.RIGHT, 1)));
        assertEquals(Option.of(LEAD), DiffStops.nearest(STOPS, new Anchor.File("c.txt")));
    }

    @Test
    void aThreadIsFoundByItsIdWhereverItIsElseItGoesToItsLine() {
        Seq<Anchor> stops =
                STOPS.insert(6, new Anchor.Thread(new ReviewThread.Id("L"), Option.of(line(Side.RIGHT, 3))));

        assertEquals(
                Option.of(new Anchor.Thread(new ReviewThread.Id("L"), Option.of(line(Side.RIGHT, 3)))),
                DiffStops.nearest(stops, new Anchor.Thread(new ReviewThread.Id("L"), Option.of(line(Side.RIGHT, 2)))));
        assertEquals(
                Option.of(line(Side.RIGHT, 2)),
                DiffStops.nearest(
                        stops, new Anchor.Thread(new ReviewThread.Id("gone"), Option.of(line(Side.RIGHT, 2)))));
        assertEquals(
                Option.of(LEAD),
                DiffStops.nearest(stops, new Anchor.Thread(new ReviewThread.Id("gone"), Option.none())));
    }

    /**
     * The thread with the id {@code id} on line 2, with replies {@code R1} and {@code R2}, resolved if {@code resolved}.
     */
    private static ReviewThread replied(String id, boolean resolved) {
        ReviewThread thread = thread(id, Subject.LINE, Option.of(2));
        Comment first = thread.comments().head();
        return new ReviewThread(
                thread.path(),
                thread.side(),
                thread.line(),
                thread.startLine(),
                thread.startSide(),
                thread.originalLine(),
                resolved,
                false,
                thread.subject(),
                List.of(
                        first,
                        new Comment(
                                "ann",
                                Instant.EPOCH,
                                "Yes",
                                Option.none(),
                                new ReviewThread.Comment.Id("R1"),
                                new ReviewThread.Comment.RestId("2"),
                                CommentRights.ALL),
                        new Comment(
                                "bob",
                                Instant.EPOCH,
                                "No",
                                Option.none(),
                                new ReviewThread.Comment.Id("R2"),
                                new ReviewThread.Comment.RestId("3"),
                                CommentRights.ALL)),
                0,
                new ReviewThread.Id(id),
                ReviewThread.Rights.ALL);
    }

    @Test
    void eachCommentOfAnOpenThreadButItsFirstIsAStopOfItsOwn() {
        Anchor.Thread thread = new Anchor.Thread(new ReviewThread.Id("L"), Option.of(line(Side.RIGHT, 2)));
        List<Anchor> threads = List.of(
                thread,
                new Anchor.Comment(new ReviewThread.Comment.Id("R1"), thread),
                new Anchor.Comment(new ReviewThread.Comment.Id("R2"), thread));

        assertEquals(threads, DiffStops.anchors(replied("L", false), thread.line(), Collapsed.NONE));
        // A resolved thread is collapsed, one stop, until it is opened; an open one is collapsed by hand.
        assertEquals(List.of(thread), DiffStops.anchors(replied("L", true), thread.line(), Collapsed.NONE));
        assertEquals(
                threads,
                DiffStops.anchors(
                        replied("L", true),
                        thread.line(),
                        Collapsed.NONE.flipped(new Collapsed.Key("L"), List.of(replied("L", true)))));
        assertEquals(
                List.of(thread),
                DiffStops.anchors(
                        replied("L", false),
                        thread.line(),
                        Collapsed.NONE.flipped(new Collapsed.Key("L"), List.of(replied("L", false)))));
        // A collapsed comment is still a stop.
        assertEquals(
                threads,
                DiffStops.anchors(
                        replied("L", false),
                        thread.line(),
                        Collapsed.NONE.flipped(new Collapsed.Key("R1"), List.of(replied("L", false)))));
    }

    @Test
    void aCommentIsFoundByItsIdElseItGoesToItsThread() {
        Anchor.Thread thread = new Anchor.Thread(new ReviewThread.Id("L"), Option.of(line(Side.RIGHT, 2)));
        Seq<Anchor> open = STOPS.insertAll(
                5,
                List.of(
                        thread,
                        new Anchor.Comment(new ReviewThread.Comment.Id("R1"), thread),
                        new Anchor.Comment(new ReviewThread.Comment.Id("R2"), thread)));
        Seq<Anchor> collapsed = STOPS.insert(5, thread);

        assertEquals(
                Option.of(new Anchor.Comment(new ReviewThread.Comment.Id("R2"), thread)),
                DiffStops.nearest(
                        open,
                        new Anchor.Comment(
                                new ReviewThread.Comment.Id("R2"),
                                new Anchor.Thread(new ReviewThread.Id("L"), Option.none()))));
        assertEquals(
                Option.of(thread),
                DiffStops.nearest(collapsed, new Anchor.Comment(new ReviewThread.Comment.Id("R2"), thread)));
        assertEquals(
                Option.of(line(Side.RIGHT, 2)),
                DiffStops.nearest(
                        open,
                        new Anchor.Comment(
                                new ReviewThread.Comment.Id("gone"),
                                new Anchor.Thread(new ReviewThread.Id("gone"), thread.line()))));
    }

    @Test
    void withoutStopsTheCursorIsNowhere() {
        assertEquals(Option.none(), DiffStops.nearest(List.empty(), LEAD));
    }
}
