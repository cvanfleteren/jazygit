package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine.Kind;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.ModeChange;
import org.junit.jupiter.api.Test;

class UnifiedDiffTest {

    @Test
    void keepsWhatComesBeforeTheFirstFile() {
        UnifiedDiff diff = UnifiedDiff.parse("""
                 a.txt | 1 +
                 1 file changed, 1 insertion(+)

                diff --git a/a.txt b/a.txt
                index 5626abf..814f4a4 100644
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1,2 @@
                 one
                +two
                """);

        assertEquals(List.of(" a.txt | 1 +", " 1 file changed, 1 insertion(+)", ""), diff.preamble());
        assertEquals(1, diff.files().size());
    }

    @Test
    void numbersTheLinesOfAHunkInTheOldAndTheNewFile() {
        FileDiff file = UnifiedDiff.parse("""
                diff --git a/a.txt b/a.txt
                index 5626abf..814f4a4 100644
                --- a/a.txt
                +++ b/a.txt
                @@ -3,4 +3,4 @@ section
                 three
                -four
                +FOUR
                +five
                 six
                \\ No newline at end of file
                """).files().head();

        assertEquals(
                new FileDiff(
                        ChangeType.MODIFIED,
                        "a.txt",
                        Option.none(),
                        Option.none(),
                        false,
                        List.of(new Hunk(
                                "@@ -3,4 +3,4 @@ section",
                                List.of(
                                        new DiffLine(Kind.CONTEXT, "three", Option.of(3), Option.of(3)),
                                        new DiffLine(Kind.REMOVED, "four", Option.of(4), Option.none()),
                                        new DiffLine(Kind.ADDED, "FOUR", Option.none(), Option.of(4)),
                                        new DiffLine(Kind.ADDED, "five", Option.none(), Option.of(5)),
                                        new DiffLine(Kind.CONTEXT, "six", Option.of(5), Option.of(6)),
                                        new DiffLine(
                                                Kind.NOTE,
                                                "\\ No newline at end of file",
                                                Option.none(),
                                                Option.none()))))),
                file);
    }

    @Test
    void tellsAddedDeletedRenamedAndModeChangedFilesApart() {
        List<FileDiff> files = UnifiedDiff.parse("""
                diff --git a/new.txt b/new.txt
                new file mode 100644
                index 0000000..5626abf
                --- /dev/null
                +++ b/new.txt
                @@ -0,0 +1 @@
                +one
                diff --git a/gone.txt b/gone.txt
                deleted file mode 100644
                index 5626abf..0000000
                --- a/gone.txt
                +++ /dev/null
                @@ -1 +0,0 @@
                -one
                diff --git a/old name.txt b/new name.txt
                similarity index 100%
                rename from old name.txt
                rename to new name.txt
                diff --git a/run.sh b/run.sh
                old mode 100644
                new mode 100755
                diff --git a/image.png b/image.png
                index 1234567..89abcde 100644
                Binary files a/image.png and b/image.png differ
                """).files();

        assertEquals(
                List.of(
                        ChangeType.ADDED,
                        ChangeType.DELETED,
                        ChangeType.RENAMED,
                        ChangeType.MODIFIED,
                        ChangeType.MODIFIED),
                files.map(FileDiff::type));
        assertEquals(List.of("new.txt", "gone.txt", "new name.txt", "run.sh", "image.png"), files.map(FileDiff::path));
        assertEquals(Option.of("old name.txt"), files.get(2).origin());
        assertTrue(files.get(2).hunks().isEmpty());
        assertEquals(Option.of(new ModeChange("100644", "100755")), files.get(3).mode());
        assertTrue(files.get(4).binary());
        assertTrue(files.get(4).hunks().isEmpty());
    }

    @Test
    void readsQuotedPathsAndPathsWithSpaces() {
        List<FileDiff> files = UnifiedDiff.parse("""
                diff --git "a/f\\303\\266o.txt" "b/f\\303\\266o.txt"
                index 5626abf..814f4a4 100644
                --- "a/f\\303\\266o.txt"
                +++ "b/f\\303\\266o.txt"
                @@ -1 +1 @@
                -one
                +two
                diff --git a/with b/space.txt b/with b/space.txt
                old mode 100644
                new mode 100755
                diff --git a/tabbed name.txt b/tabbed name.txt
                index 5626abf..814f4a4 100644
                --- a/tabbed name.txt\t
                +++ b/tabbed name.txt\t
                @@ -1 +1 @@
                -one
                +two
                """).files();

        assertEquals(List.of("föo.txt", "with b/space.txt", "tabbed name.txt"), files.map(FileDiff::path));
    }

    @Test
    void keepsTheLinesOfAFileHeaderItDoesNotKnow() {
        FileDiff file = UnifiedDiff.parse("""
                diff --git a/a.txt b/a.txt
                something new
                --- a/a.txt
                +++ b/a.txt
                @@ -1 +1 @@
                -one
                +two
                """).files().head();

        assertEquals(2, file.hunks().size());
        assertEquals(
                List.of(DiffLine.note("something new")), file.hunks().head().lines());
    }

    @Test
    void aHunkIsNumberedWhenGitsNumbersCanBeReadFromItsHeader() {
        assertTrue(new Hunk("@@ -3,4 +3,5 @@ void run() {", List.empty()).numbered());
        assertTrue(new Hunk("@@ -1 +1 @@", List.empty()).numbered());
        assertFalse(new Hunk("@@ garbled", List.empty()).numbered());
        assertFalse(new Hunk("", List.empty()).numbered());
    }

    @Test
    void anEmptyDiffHasNoFiles() {
        assertEquals(new UnifiedDiff(List.empty(), List.empty()), UnifiedDiff.parse(""));
    }
}
