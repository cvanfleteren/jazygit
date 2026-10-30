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
        return thread(path, side, line, start, side);
    }

    private static ReviewThread thread(
            String path, Side side, Option<Integer> line, Option<Integer> start, Side startSide) {
        return new ReviewThread(
                path,
                side,
                line,
                start,
                startSide,
                line,
                false,
                line.isEmpty(),
                Subject.LINE,
                List.of(new Comment("bob", Instant.EPOCH, "Hm", Option.none(), "C", "1")),
                0,
                "T",
                ReviewThread.Rights.ALL);
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
        // The removed line among them is covered too, as GitHub highlights it.
        assertEquals(
                List.of(true, true, true, false),
                placed.files().head().lines().head().map(LineRemarks::covered));
    }

    @Test
    void aThreadThatStartsOnTheOtherSideCoversFromThere() {
        // From the removed line two of the old file to line three of the new one.
        ReviewThread across = thread("a.txt", Side.RIGHT, Option.of(3), Option.of(2), Side.LEFT);

        PlacedRemarks placed = PlacedRemarks.place(DIFF, List.of(across));

        assertEquals(
                List.of(false, true, true, false),
                placed.files().head().lines().head().map(LineRemarks::covered));
    }

    @Test
    void aThreadWhoseFirstLineTheDiffDoesNotShowCoversFromTheFirstLineShownAfterIt() {
        // As with whitespace ignored: GitHub's selection starts at a line before the hunk git made.
        UnifiedDiff later = UnifiedDiff.parse("""
                diff --git a/a.txt b/a.txt
                index 1111111..2222222 100644
                --- a/a.txt
                +++ b/a.txt
                @@ -15,3 +15,3 @@
                 fifteen
                -sixteen
                +SIXTEEN
                 seventeen
                """);
        ReviewThread fromBefore = thread("a.txt", Side.RIGHT, Option.of(17), Option.of(5));
        ReviewThread fromBeforeOnTheOtherSide = thread("a.txt", Side.RIGHT, Option.of(17), Option.of(5), Side.LEFT);

        for (ReviewThread thread : List.of(fromBefore, fromBeforeOnTheOtherSide)) {
            FileRemarks file =
                    PlacedRemarks.place(later, List.of(thread)).files().head();

            assertEquals(
                    List.of(List.empty(), List.empty(), List.empty(), List.of(thread)),
                    file.lines().head().map(LineRemarks::threads));
            assertEquals(List.of(true, true, true, false), file.lines().head().map(LineRemarks::covered));
        }
    }

    @Test
    void aThreadWhoseFirstLineIsInAGapCoversTheHunksBetweenItAndItsLast() {
        // Whitespace ignored cut GitHub's one hunk in two, and left out the first line of the thread, ten.
        UnifiedDiff cut = UnifiedDiff.parse("""
                diff --git a/a.txt b/a.txt
                index 1111111..2222222 100644
                --- a/a.txt
                +++ b/a.txt
                @@ -2,2 +2,2 @@
                 two
                -three
                +THREE
                @@ -15,2 +15,2 @@
                 fifteen
                -sixteen
                +SIXTEEN
                @@ -35,2 +35,2 @@
                 thirty-five
                -thirty-six
                +THIRTY-SIX
                """);
        ReviewThread fromTheGap = thread("a.txt", Side.RIGHT, Option.of(36), Option.of(10));

        FileRemarks file = PlacedRemarks.place(cut, List.of(fromTheGap)).files().head();

        assertEquals(
                List.of(List.of(false, false, false), List.of(true, true, true), List.of(true, true, false)),
                file.lines().map(hunk -> hunk.map(LineRemarks::covered)));
        assertEquals(List.of(fromTheGap), file.lines().last().last().threads());
    }

    @Test
    void aThreadOnOneLineCoversNothing() {
        assertTrue(PlacedRemarks.place(DIFF, List.of(onLine(Side.RIGHT, 3)))
                .files()
                .head()
                .lines()
                .head()
                .forAll(line -> !line.covered()));
    }

    @Test
    void aThreadOnSeveralLinesThatCannotBePlacedCoversNothing() {
        ReviewThread beyond = thread("a.txt", Side.RIGHT, Option.of(40), Option.of(1));

        FileRemarks file = PlacedRemarks.place(DIFF, List.of(beyond)).files().head();

        assertEquals(List.of(beyond), file.unplaced());
        assertTrue(file.lines().head().forAll(line -> !line.covered()));
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
                Side.RIGHT,
                Option.none(),
                false,
                false,
                Subject.FILE,
                List.empty(),
                0,
                "T",
                ReviewThread.Rights.ALL);

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

    @Test
    void aThreadOnARenamedFileHasItsNewPathOnEitherSideAsGitHubGivesIt() {
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
        ReviewThread left = thread("new.txt", Side.LEFT, Option.of(1), Option.none());
        ReviewThread right = thread("new.txt", Side.RIGHT, Option.of(1), Option.none());

        PlacedRemarks placed = PlacedRemarks.place(renamed, List.of(left, right));

        assertEquals(
                List.of(List.of(left), List.of(right)),
                placed.files().head().lines().head().map(LineRemarks::threads));
        assertEquals(List.empty(), placed.files().head().unplaced());
    }

    @Test
    void aThreadOnADeletedFileHasItsOldPath() {
        UnifiedDiff deleted = UnifiedDiff.parse("""
                diff --git a/gone.txt b/gone.txt
                deleted file mode 100644
                index 1111111..0000000
                --- a/gone.txt
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -x
                -y
                """);
        ReviewThread left = thread("gone.txt", Side.LEFT, Option.of(2), Option.none());

        PlacedRemarks placed = PlacedRemarks.place(deleted, List.of(left));

        assertEquals(
                List.of(List.empty(), List.of(left)),
                placed.files().head().lines().head().map(LineRemarks::threads));
        assertEquals(List.empty(), placed.files().head().unplaced());
    }
}
