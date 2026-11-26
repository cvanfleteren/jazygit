package net.vanfleteren.jazygit.kernel.state.diff;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor.OnLine;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread.Subject;
import org.junit.jupiter.api.Test;

/**
 * Where the cursor of a rich diff stops, and which stop it is on once its own is gone (ADR 60).
 */
class DiffStopsTest {

    private static final Anchor LEAD = new Anchor.Lead();
    private static final Anchor A = new Anchor.File(new RepoPath("a.txt"));
    private static final Anchor B = new Anchor.File(new RepoPath("b.png"));

    private static OnLine line(DiffSide side, int number) {
        return new OnLine(new RepoPath("a.txt"), side, number);
    }

    private static RemarkThread thread(String id, Subject subject, Option<Integer> line) {
        return new RemarkThread(
                new RemarkThread.Id(id),
                new RepoPath("a.txt"),
                Option.none(),
                subject,
                line,
                DiffSide.NEW,
                Option.none(),
                Option.of(DiffSide.NEW),
                line,
                false,
                false,
                0,
                List.of(new Remark(new Remark.Id("C"), "bob", Instant.EPOCH, "Hm", Option.none())));
    }

    // The stops of a diff without threads, as the rich view draws them: a.txt, its line 2 changed, in two hunks; then
    // b.txt, binary.
    private static final List<Anchor> STOPS = List.of(
            LEAD,
            A,
            line(DiffSide.NEW, 1),
            line(DiffSide.OLD, 2),
            line(DiffSide.NEW, 2),
            line(DiffSide.NEW, 3),
            line(DiffSide.NEW, 10),
            B);

    @Test
    void anAnchorThatIsAStopIsItsOwnStop() {
        assertEquals(Option.of(line(DiffSide.OLD, 2)), DiffStops.nearest(STOPS, line(DiffSide.OLD, 2)));
        assertEquals(Option.of(B), DiffStops.nearest(STOPS, B));
    }

    @Test
    void aMarkerThatIsGoneGoesToTheMarkerOfItsFileThatStartsClosestElseToTheLineWhereItStarted() {
        Anchor.Gap above = new Anchor.Gap(new RepoPath("a.txt"), 4, Option.of(10));
        Anchor.Gap after = new Anchor.Gap(new RepoPath("a.txt"), 11, Option.none());
        List<Anchor> stops = List.of(A, line(DiffSide.NEW, 3), above, line(DiffSide.NEW, 10), after, B);

        // Expanding ten lines down from the hunk above moves where the marker starts.
        assertEquals(
                Option.of(above), DiffStops.nearest(stops, new Anchor.Gap(new RepoPath("a.txt"), 1, Option.of(10))));
        assertEquals(
                Option.of(after), DiffStops.nearest(stops, new Anchor.Gap(new RepoPath("a.txt"), 14, Option.none())));
        // Expanding all of it leaves no marker: the cursor goes to the line where it started.
        assertEquals(
                Option.of(line(DiffSide.NEW, 10)),
                DiffStops.nearest(STOPS, new Anchor.Gap(new RepoPath("a.txt"), 8, Option.of(10))));
    }

    @Test
    void aLineThatIsGoneGoesToTheClosestOfItsSideElseToItsFileElseToTheFirst() {
        assertEquals(Option.of(line(DiffSide.NEW, 10)), DiffStops.nearest(STOPS, line(DiffSide.NEW, 8)));
        assertEquals(Option.of(line(DiffSide.OLD, 2)), DiffStops.nearest(STOPS, line(DiffSide.OLD, 40)));
        assertEquals(Option.of(B), DiffStops.nearest(STOPS, new OnLine(new RepoPath("b.png"), DiffSide.NEW, 1)));
        assertEquals(Option.of(LEAD), DiffStops.nearest(STOPS, new OnLine(new RepoPath("c.txt"), DiffSide.NEW, 1)));
        assertEquals(Option.of(LEAD), DiffStops.nearest(STOPS, new Anchor.File(new RepoPath("c.txt"))));
    }

    @Test
    void aThreadIsFoundByItsIdWhereverItIsElseItGoesToItsLine() {
        Seq<Anchor> stops =
                STOPS.insert(6, new Anchor.Thread(new RemarkThread.Id("L"), Option.of(line(DiffSide.NEW, 3))));

        assertEquals(
                Option.of(new Anchor.Thread(new RemarkThread.Id("L"), Option.of(line(DiffSide.NEW, 3)))),
                DiffStops.nearest(
                        stops, new Anchor.Thread(new RemarkThread.Id("L"), Option.of(line(DiffSide.NEW, 2)))));
        assertEquals(
                Option.of(line(DiffSide.NEW, 2)),
                DiffStops.nearest(
                        stops, new Anchor.Thread(new RemarkThread.Id("gone"), Option.of(line(DiffSide.NEW, 2)))));
        assertEquals(
                Option.of(LEAD),
                DiffStops.nearest(stops, new Anchor.Thread(new RemarkThread.Id("gone"), Option.none())));
    }

    /**
     * The thread with the id {@code id} on line 2, with replies {@code R1} and {@code R2}, resolved if {@code resolved}.
     */
    private static RemarkThread replied(String id, boolean resolved) {
        RemarkThread thread = thread(id, Subject.LINE, Option.of(2));
        Remark first = thread.remarks().head();
        return new RemarkThread(
                new RemarkThread.Id(id),
                thread.path(),
                Option.none(),
                thread.subject(),
                thread.line(),
                thread.side(),
                thread.startLine(),
                thread.startSide(),
                thread.originalLine(),
                false,
                resolved,
                0,
                List.of(
                        first,
                        new Remark(new Remark.Id("R1"), "ann", Instant.EPOCH, "Yes", Option.none()),
                        new Remark(new Remark.Id("R2"), "bob", Instant.EPOCH, "No", Option.none())));
    }

    @Test
    void eachCommentOfAnOpenThreadButItsFirstIsAStopOfItsOwn() {
        Anchor.Thread thread = new Anchor.Thread(new RemarkThread.Id("L"), Option.of(line(DiffSide.NEW, 2)));
        List<Anchor> threads = List.of(
                thread, new Anchor.Remark(new Remark.Id("R1"), thread), new Anchor.Remark(new Remark.Id("R2"), thread));

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
        Anchor.Thread thread = new Anchor.Thread(new RemarkThread.Id("L"), Option.of(line(DiffSide.NEW, 2)));
        Seq<Anchor> open = STOPS.insertAll(
                5,
                List.of(
                        thread,
                        new Anchor.Remark(new Remark.Id("R1"), thread),
                        new Anchor.Remark(new Remark.Id("R2"), thread)));
        Seq<Anchor> collapsed = STOPS.insert(5, thread);

        assertEquals(
                Option.of(new Anchor.Remark(new Remark.Id("R2"), thread)),
                DiffStops.nearest(
                        open,
                        new Anchor.Remark(
                                new Remark.Id("R2"), new Anchor.Thread(new RemarkThread.Id("L"), Option.none()))));
        assertEquals(Option.of(thread), DiffStops.nearest(collapsed, new Anchor.Remark(new Remark.Id("R2"), thread)));
        assertEquals(
                Option.of(line(DiffSide.NEW, 2)),
                DiffStops.nearest(
                        open,
                        new Anchor.Remark(
                                new Remark.Id("gone"), new Anchor.Thread(new RemarkThread.Id("gone"), thread.line()))));
    }

    @Test
    void aRowOfJsonRenderedIsFoundByItsPathElseAtTheClosestNodeItIsInElseAsItsLine() {
        Anchor.Node jobs = new Anchor.Node(new RepoPath("a.txt"), "/jobs", line(DiffSide.NEW, 2));
        Anchor.Node build = new Anchor.Node(new RepoPath("a.txt"), "/jobs/0", line(DiffSide.NEW, 3));
        List<Anchor> stops = List.of(A, jobs, build);

        // The original draws the same node from another line.
        assertEquals(
                Option.of(build),
                DiffStops.nearest(stops, new Anchor.Node(new RepoPath("a.txt"), "/jobs/0", line(DiffSide.OLD, 9))));
        // A node closed over it.
        assertEquals(
                Option.of(jobs),
                DiffStops.nearest(
                        List.of(A, jobs),
                        new Anchor.Node(new RepoPath("a.txt"), "/jobs/0/steps", line(DiffSide.NEW, 5))));
        // Not the node /job, whose path only starts as /jobs does: the row drawn from the closest line.
        Anchor.Node job = new Anchor.Node(new RepoPath("a.txt"), "/job", line(DiffSide.NEW, 1));
        Anchor.Node close = new Anchor.Node(new RepoPath("a.txt"), "/x", line(DiffSide.NEW, 3));
        assertEquals(Option.of(close), DiffStops.nearest(List.of(A, job, close), build));
    }

    @Test
    void aLineGoesToTheFirstRowOfJsonRenderedDrawnFromTheClosestLine() {
        Anchor.Node first = new Anchor.Node(new RepoPath("a.txt"), "/a", line(DiffSide.NEW, 2));
        Anchor.Node second = new Anchor.Node(new RepoPath("a.txt"), "/b", line(DiffSide.NEW, 2));
        Anchor.Node far = new Anchor.Node(new RepoPath("a.txt"), "/c", line(DiffSide.NEW, 9));

        assertEquals(Option.of(first), DiffStops.nearest(List.of(A, first, second, far), line(DiffSide.NEW, 4)));
    }

    @Test
    void withoutStopsTheCursorIsNowhere() {
        assertEquals(Option.none(), DiffStops.nearest(List.empty(), LEAD));
    }
}
