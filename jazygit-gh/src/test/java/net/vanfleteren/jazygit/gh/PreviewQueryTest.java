package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import org.junit.jupiter.api.Test;

/**
 * The preview of a pull request of another repository: its base and body from {@code gh pr view --json}, and its
 * diff.
 */
class PreviewQueryTest {

    @Test
    void theBaseAndTheBodyAreRead() {
        assertEquals(
                Either.right(Tuple.of("main", "Fixes the login.\n\nSee #3.\n")),
                GhProcess.decoded("""
                        {"baseRefName": "main", "body": "Fixes the login.\\n\\nSee #3.\\n"}""", PreviewQuery.DECODER));
    }

    @Test
    void thePreviewHasTheBodyWithoutTrailingWhitespace() {
        assertEquals(
                new PullRequestPreview("main", "Fixes the login.\n\nSee #3.", "+a\n", false),
                PreviewQuery.preview("main", "Fixes the login.\n\nSee #3.\n", "+a\n"));
        assertEquals(new PullRequestPreview("main", "", "", false), PreviewQuery.preview("main", "", ""));
    }

    @Test
    void aLongDiffIsCutOff() {
        String diff = "+line\n".repeat(PreviewQuery.MAX_DIFF_LINES + 10);

        PullRequestPreview preview = PreviewQuery.preview("main", "", diff);

        assertTrue(preview.truncated());
        assertEquals(PreviewQuery.MAX_DIFF_LINES, preview.diff().lines().count());
    }

    @Test
    void aDiffOfJustTheMostLinesIsWhole() {
        String diff = "+line\n".repeat(PreviewQuery.MAX_DIFF_LINES);

        assertEquals(new PullRequestPreview("main", "", diff, false), PreviewQuery.preview("main", "", diff));
    }

    @Test
    void aDiffOfLongLinesIsCutOffAtTheLastWholeLineThatFits() {
        // 1000 characters with its line end: as many fit as there are thousands in the most characters.
        String line = "+" + "x".repeat(998) + "\n";
        String fits = line.repeat(PreviewQuery.MAX_DIFF_CHARS / 1000);

        assertFalse(PreviewQuery.preview("main", "", fits).truncated());
        PullRequestPreview cut = PreviewQuery.preview("main", "", fits + line);
        assertTrue(cut.truncated());
        assertEquals(fits, cut.diff());
    }

    @Test
    void aDiffOfCrlfLinesIsCutOffAsGhWroteIt() {
        // A \r is part of its line: counted, and kept, as gh wrote it; one in the middle of a line ends nothing.
        String line = "+" + "x".repeat(497) + "\r" + "y".repeat(499) + "\r\n";
        String fits = line.repeat(PreviewQuery.MAX_DIFF_CHARS / 1000);

        assertEquals(new PullRequestPreview("main", "", fits, false), PreviewQuery.preview("main", "", fits));
        assertEquals(new PullRequestPreview("main", "", fits, true), PreviewQuery.preview("main", "", fits + line));
    }

    @Test
    void thePreviewIsAskedWithoutJq() {
        assertEquals(
                List.of("gh", "pr", "view", "https://github.com/o/r/pull/9", "--json", "baseRefName,body"),
                PreviewQuery.command("https://github.com/o/r/pull/9").executed().commandLine());
    }
}
