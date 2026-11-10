package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import org.junit.jupiter.api.Test;

class FileVersionTest {

    /**
     * Five lines read whole: line 2 changed, line 4 removed, and a line added at the end without a newline.
     */
    private static final String WHOLE = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,5 +1,5 @@
             one
            -two
            +TWO
             three
            -four
             five
            +six
            \\ No newline at end of file
            """;

    @Test
    void theNewVersionHasTheContextAndTheAddedLines() {
        ParsedFile version = FileVersion.NEW.of(whole());

        assertEquals(List.of("one", "TWO", "three", "five", "six", "\\ No newline at end of file"), texts(version));
        assertEquals(1, version.hunks().size());
        assertEquals("", version.hunks().head().hunk().header());
    }

    @Test
    void theOriginalHasTheContextAndTheRemovedLines() {
        assertEquals(List.of("one", "two", "three", "four", "five"), texts(FileVersion.ORIGINAL.of(whole())));
    }

    @Test
    void aVersionKeepsTheWordsOfItsLines() {
        ParsedLine two = FileVersion.ORIGINAL.of(whole()).hunks().head().lines().get(1);

        assertEquals("two", two.line().text());
        assertEquals(DiffLine.Kind.REMOVED, two.line().kind());
        assertFalse(two.words().isEmpty());
    }

    @Test
    void aVersionWithoutLinesHasNoHunk() {
        ParsedFile added = ParsedDiff.of(
                        "diff --git a/n b/n\nnew file mode 100644\n--- /dev/null\n+++ b/n\n" + "@@ -0,0 +1 @@\n+new\n")
                .files()
                .head();

        assertTrue(FileVersion.ORIGINAL.of(added).hunks().isEmpty());
    }

    @Test
    void theNumberOfALineIsThatOfItsVersion() {
        DiffLine removed = new DiffLine(DiffLine.Kind.REMOVED, "four", Option.of(4), Option.none());

        assertEquals(Option.of(4), FileVersion.ORIGINAL.number(removed));
        assertEquals(Option.none(), FileVersion.NEW.number(removed));
    }

    @Test
    void theCycleGoesFromTheDiffToTheNewVersionToTheOriginal() {
        assertEquals(Option.of(FileVersion.NEW), FileVersion.after(Option.none(), ChangeType.MODIFIED));
        assertEquals(
                Option.of(FileVersion.ORIGINAL), FileVersion.after(Option.of(FileVersion.NEW), ChangeType.MODIFIED));
        assertEquals(Option.none(), FileVersion.after(Option.of(FileVersion.ORIGINAL), ChangeType.MODIFIED));
    }

    @Test
    void theCycleLeavesOutAVersionAFileDoesNotHave() {
        assertEquals(Option.none(), FileVersion.after(Option.of(FileVersion.NEW), ChangeType.ADDED));
        assertEquals(Option.of(FileVersion.ORIGINAL), FileVersion.after(Option.none(), ChangeType.DELETED));
        assertEquals(Option.none(), FileVersion.after(Option.of(FileVersion.ORIGINAL), ChangeType.DELETED));
    }

    @Test
    void aFileOfLinesHasVersions() {
        assertTrue(FileVersion.shown(whole().file()));
    }

    @Test
    void aSubmoduleHasNone() {
        String diff = "diff --git a/sub b/sub\nindex 1111111..2222222 160000\n--- a/sub\n+++ b/sub\n@@ -1 +1 @@\n"
                + "-Subproject commit 1111111111111111111111111111111111111111\n"
                + "+Subproject commit 2222222222222222222222222222222222222222\n";

        assertFalse(FileVersion.shown(ParsedDiff.of(diff).files().head().file()));
    }

    @Test
    void aBinaryFileAndAFileWhoseModeOnlyChangedHaveNone() {
        String binary = "diff --git a/b.png b/b.png\nindex 1..2 100644\nBinary files a/b.png and b/b.png differ\n";
        String mode = "diff --git a/s.sh b/s.sh\nold mode 100644\nnew mode 100755\n";

        assertFalse(FileVersion.shown(ParsedDiff.of(binary).files().head().file()));
        assertFalse(FileVersion.shown(ParsedDiff.of(mode).files().head().file()));
    }

    private static ParsedFile whole() {
        return ParsedDiff.of(WHOLE).files().head();
    }

    private static List<String> texts(ParsedFile file) {
        return file.hunks().flatMap(ParsedHunk::lines).map(line -> line.line().text());
    }
}
