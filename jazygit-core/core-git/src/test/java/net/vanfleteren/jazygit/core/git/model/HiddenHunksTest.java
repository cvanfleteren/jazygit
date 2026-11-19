package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.core.git.model.HiddenHunks.Hidden;
import net.vanfleteren.jazygit.core.git.model.HiddenHunks.Piece;
import net.vanfleteren.jazygit.core.git.model.ParsedDiff.ParsedFile;
import org.junit.jupiter.api.Test;

class HiddenHunksTest {

    private static final DiffFilter IMPORTS = DiffFilter.of(
                    "java-imports",
                    Option.of("Java imports"),
                    List.of("java"),
                    List.empty(),
                    Option.of(List.of("^import ")))
            .get();

    /**
     * Twenty lines: an import changed on line 2, a line of code on line 15.
     */
    private static final String GIT = """
            diff --git a/A.java b/A.java
            --- a/A.java
            +++ b/A.java
            @@ -1,5 +1,5 @@
             package a;
            -import a.B;
            +import a.C;
             x3
             x4
             x5
            @@ -12,7 +12,7 @@
             x12
             x13
             x14
            -x15
            +y15
             x16
             x17
             x18
            """;

    private static final String WHOLE = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,20 +1,20 @@\n"
            + List.rangeClosed(1, 20)
                    .map(n -> switch (n) {
                        case 1 -> " package a;\n";
                        case 2 -> "-import a.B;\n+import a.C;\n";
                        case 15 -> "-x15\n+y15\n";
                        default -> " x" + n + "\n";
                    })
                    .mkString();

    @Test
    void theHunksOfImportsOnlyAreHidden() {
        List<Hidden> hidden = HiddenHunks.of(git(), List.of(IMPORTS), hunk -> false);

        assertEquals(List.of(new HunkStart(1, 1)), hidden.map(Hidden::start));
        assertEquals(List.of("Java imports"), hidden.head().filters());
        assertEquals(2, hidden.head().changes());
    }

    @Test
    void aHunkKeptIsNotHidden() {
        assertEquals(
                List.empty(),
                HiddenHunks.of(git(), List.of(IMPORTS), hunk -> hunk.header().startsWith("@@ -1,")));
    }

    @Test
    void aHiddenHunkAsGitGaveItIsOnePieceAsItWas() {
        ParsedFile git = git();
        List<Piece> pieces = HiddenHunks.split(git, HiddenHunks.of(git, List.of(IMPORTS), hunk -> false));

        assertEquals(2, pieces.size());
        assertTrue(pieces.head().hidden().isDefined());
        assertEquals(git.hunks().head(), pieces.head().hunk());
        assertEquals(Option.none(), pieces.last().hidden());
        assertEquals(git.hunks().last(), pieces.last().hunk());
    }

    @Test
    void aFoldedHunkIsCutAroundTheLinesOfAHiddenOne() {
        ParsedFile whole = ParsedDiff.of(WHOLE).files().head();
        // Five lines of context join the two changes into one hunk.
        ParsedFile folded = Folding.fold(whole, 6, List.empty());
        assertEquals(1, folded.hunks().size());

        List<Piece> pieces = HiddenHunks.split(folded, HiddenHunks.of(git(), List.of(IMPORTS), hunk -> false));

        assertEquals(List.of(true, false), pieces.map(piece -> piece.hidden().isDefined()));
        assertEquals("@@ -1,5 +1,5 @@", pieces.head().hunk().hunk().header());
        assertEquals("@@ -6,15 +6,15 @@", pieces.last().hunk().hunk().header());
        assertEquals(
                folded.hunks().head().lines(),
                pieces.flatMap(piece -> piece.hunk().lines()));
    }

    @Test
    void aHunkWithNothingHiddenIsOnePiece() {
        ParsedFile git = git();

        List<Piece> pieces = HiddenHunks.split(git, List.empty());

        assertEquals(git.hunks(), pieces.map(Piece::hunk));
        assertTrue(pieces.forAll(piece -> piece.hidden().isEmpty()));
    }

    private static ParsedFile git() {
        return ParsedDiff.of(GIT).files().head();
    }
}
