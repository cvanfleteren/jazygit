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
import net.vanfleteren.jazygit.state.PlacedRemarks.FileRemarks;
import net.vanfleteren.jazygit.state.PlacedRemarks.LineRemarks;
import org.junit.jupiter.api.Test;

/**
 * Review threads placed in a parsed diff, by the path, side and number of their line (ADR 55).
 */
class PlacedRemarksTest {

    // One file, a.txt: its line 2 changed, so line 2 of the old file is removed and line 2 of the new one added.
    private static final UnifiedDiff DIFF = UnifiedDiff.parse("""
            diff --git a/a.txt b/a.txt
            index 1111111..2222222 100644
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            """);

    private static ReviewThread thread(String path, Side side, Option<Integer> line, Option<Integer> start) {
        return new ReviewThread(
                path,
                side,
                line,
                start,
                line,
                false,
                line.isEmpty(),
                Subject.LINE,
                List.of(new Comment("bob", Instant.EPOCH, "Hm", Option.none())),
                0);
    }

    private static ReviewThread onLine(Side side, int line) {
        return thread("a.txt", side, Option.of(line), Option.none());
    }

    private static List<List<ReviewThread>> afterEachLine(PlacedRemarks placed) {
        return placed.files().head().lines().head().map(LineRemarks::threads);
    }

    @Test
    void aThreadGoesAfterTheLineWithItsNumberOnItsSide() {
        ReviewThread added = onLine(Side.RIGHT, 2);
        ReviewThread removed = onLine(Side.LEFT, 2);
        ReviewThread context = onLine(Side.LEFT, 3);

        PlacedRemarks placed = PlacedRemarks.place(DIFF, List.of(added, removed, context));

        assertEquals(List.of(List.empty(), List.of(removed), List.of(added), List.of(context)), afterEachLine(placed));
        assertEquals(List.empty(), placed.files().head().unplaced());
        assertFalse(placed.isEmpty());
    }

    @Test
    void aThreadOnSeveralLinesGoesAfterItsLastAndCoversTheOthers() {
        ReviewThread several = thread("a.txt", Side.RIGHT, Option.of(3), Option.of(1));

        PlacedRemarks placed = PlacedRemarks.place(DIFF, List.of(several));

        assertEquals(List.of(List.empty(), List.empty(), List.empty(), List.of(several)), afterEachLine(placed));
        // The removed line has no number in the new file, so it is not covered.
        assertEquals(
                List.of(true, false, true, false),
                placed.files().head().lines().head().map(LineRemarks::covered));
    }

    @Test
    void anOutdatedThreadAndOneOnALineTheDiffDoesNotShowAreListedAtTheEndOfTheirFile() {
        ReviewThread outdated = thread("a.txt", Side.RIGHT, Option.none(), Option.none());
        ReviewThread elsewhere = onLine(Side.RIGHT, 40);

        FileRemarks file =
                PlacedRemarks.place(DIFF, List.of(outdated, elsewhere)).files().head();

        assertEquals(List.of(outdated, elsewhere), file.unplaced());
        assertTrue(file.lines().head().forAll(line -> line.threads().isEmpty()));
    }

    @Test
    void aThreadAboutAWholeFileGoesUnderItsBar() {
        ReviewThread onFile = new ReviewThread(
                "a.txt",
                Side.RIGHT,
                Option.none(),
                Option.none(),
                Option.none(),
                false,
                false,
                Subject.FILE,
                List.empty(),
                0);

        FileRemarks file = PlacedRemarks.place(DIFF, List.of(onFile)).files().head();

        assertEquals(List.of(onFile), file.onFile());
        assertEquals(List.empty(), file.unplaced());
    }

    @Test
    void aThreadOfAnotherFileIsLeftOutAndOneOfARenamedFileFindsItByEitherPath() {
        UnifiedDiff renamed = UnifiedDiff.parse("""
                diff --git a/old.txt b/new.txt
                similarity index 80%
                rename from old.txt
                rename to new.txt
                --- a/old.txt
                +++ b/new.txt
                @@ -1 +1 @@
                -x
                +y
                """);
        ReviewThread byNew = thread("new.txt", Side.RIGHT, Option.of(1), Option.none());
        ReviewThread byOld = thread("old.txt", Side.LEFT, Option.of(1), Option.none());

        PlacedRemarks placed = PlacedRemarks.place(renamed, List.of(byNew, byOld, onLine(Side.RIGHT, 1)));

        assertEquals(
                List.of(List.of(byOld), List.of(byNew)),
                placed.files().head().lines().head().map(LineRemarks::threads));
        assertEquals(List.empty(), placed.files().head().unplaced());
        assertTrue(PlacedRemarks.place(renamed, List.of(onLine(Side.RIGHT, 1))).isEmpty());
    }
}
