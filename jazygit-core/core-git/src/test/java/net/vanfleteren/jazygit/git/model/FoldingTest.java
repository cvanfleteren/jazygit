package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Folding.Gap;
import net.vanfleteren.jazygit.git.model.Folding.Lines;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import org.junit.jupiter.api.Test;

class FoldingTest {

    /**
     * Twenty lines, with line 5 changed and line 15 removed.
     */
    private static final String WHOLE = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,20 +1,19 @@\n"
            + List.rangeClosed(1, 20)
                    .map(n -> switch (n) {
                        case 5 -> "-line5\n+changed\n";
                        case 15 -> "-line15\n";
                        default -> " line" + n + "\n";
                    })
                    .mkString();

    @Test
    void foldedToGitsContextAFileHasGitsHunks() {
        ParsedFile folded = Folding.fold(whole(), 3, List.empty());

        assertEquals(
                List.of("@@ -2,7 +2,7 @@", "@@ -12,7 +12,6 @@"),
                folded.hunks().map(hunk -> hunk.hunk().header()));
        assertEquals(List.rangeClosed(2, 8), numbers(folded.hunks().head()));
    }

    @Test
    void hunksCloseEnoughAreOne() {
        ParsedFile folded = Folding.fold(whole(), 5, List.empty());

        assertEquals(
                List.of("@@ -1,20 +1,19 @@"),
                folded.hunks().map(hunk -> hunk.hunk().header()));
    }

    @Test
    void expandedLinesAreShownAndJoinTheHunksTheyTouch() {
        ParsedFile folded = Folding.fold(whole(), 1, List.of(new Lines(7, 9), new Lines(18, 19)));

        assertEquals(
                List.of("@@ -4,6 +4,6 @@", "@@ -14,3 +14,2 @@", "@@ -19,2 +18,2 @@"),
                folded.hunks().map(hunk -> hunk.hunk().header()));
    }

    @Test
    void noContextShowsTheChangesOnly() {
        ParsedFile folded = Folding.fold(whole(), 0, List.empty());

        assertEquals(
                List.of("@@ -5,1 +5,1 @@", "@@ -15,1 +14,0 @@"),
                folded.hunks().map(hunk -> hunk.hunk().header()));
    }

    @Test
    void theNoteThatAFileEndsWithoutANewlineGoesWithItsLastLine() {
        ParsedFile whole = ParsedDiff.of("""
                        diff --git a/a.txt b/a.txt
                        --- a/a.txt
                        +++ b/a.txt
                        @@ -1,10 +1,10 @@
                         one
                        -two
                        +TWO
                         three
                         four
                         five
                         six
                         seven
                         eight
                         nine
                         ten
                        \\ No newline at end of file
                        """).files().head();

        ParsedFile hidden = Folding.fold(whole, 1, List.empty());
        ParsedFile shown = Folding.fold(whole, 1, List.of(new Lines(10, 10)));

        assertFalse(lines(hidden).exists(line -> line.kind() == UnifiedDiff.DiffLine.Kind.NOTE));
        assertEquals(
                List.of(" ten", "\\ No newline at end of file"),
                lines(shown.hunks().last())
                        .map(line -> line.kind() == UnifiedDiff.DiffLine.Kind.NOTE ? line.text() : " " + line.text()));
    }

    @Test
    void theGapsAreTheLinesBetweenTheHunksAndAroundThem() {
        ParsedFile folded = Folding.fold(whole(), 3, List.empty());

        assertEquals(
                List.of(new Gap(0, 1, Option.of(2)), new Gap(1, 9, Option.of(12)), new Gap(2, 18, Option.none())),
                Folding.gaps(folded.file(), Option.none()));
        assertEquals(
                List.of(new Gap(0, 1, Option.of(2)), new Gap(1, 9, Option.of(12)), new Gap(2, 18, Option.of(20))),
                Folding.gaps(folded.file(), Option.of(Folding.length(whole()))));
    }

    @Test
    void aGapAfterAHunkThatOnlyRemovesStartsAfterIt() {
        ParsedFile folded = Folding.fold(whole(), 0, List.empty());

        assertEquals(
                List.of(new Gap(0, 1, Option.of(5)), new Gap(1, 6, Option.of(15)), new Gap(2, 15, Option.of(20))),
                Folding.gaps(folded.file(), Option.of(19)));
    }

    @Test
    void aFileShownWholeLeavesNothingOutAndAnAddedFileNothingEither() {
        assertEquals(List.of(), Folding.gaps(whole().file(), Option.of(19)));
        ParsedFile added = ParsedDiff.of(
                        "diff --git a/n.txt b/n.txt\nnew file mode 100644\n--- /dev/null\n+++ b/n.txt\n"
                                + "@@ -0,0 +1,2 @@\n+one\n+two\n")
                .files()
                .head();
        assertEquals(List.of(), Folding.gaps(added.file(), Option.none()));
    }

    @Test
    void aFileReadWholeAgreesWithItsDiffUntilItsChangesDiffer() {
        ParsedFile edited =
                ParsedDiff.of(WHOLE.replace("+changed", "+edited")).files().head();

        assertTrue(Folding.agrees(whole(), Folding.fold(whole(), 3, List.empty())));
        assertFalse(Folding.agrees(whole(), Folding.fold(edited, 3, List.empty())));
    }

    private static ParsedFile whole() {
        return ParsedDiff.of(WHOLE).files().head();
    }

    private static List<UnifiedDiff.DiffLine> lines(ParsedFile file) {
        return file.hunks().flatMap(FoldingTest::lines);
    }

    private static List<UnifiedDiff.DiffLine> lines(ParsedHunk hunk) {
        return hunk.lines().map(ParsedLine::line);
    }

    private static List<Integer> numbers(ParsedHunk hunk) {
        return hunk.lines().map(ParsedLine::line).flatMap(line -> line.newNumber());
    }
}
