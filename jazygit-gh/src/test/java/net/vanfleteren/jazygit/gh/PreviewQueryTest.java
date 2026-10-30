package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void thePreviewIsAskedWithoutJq() {
        assertEquals(
                List.of("gh", "pr", "view", "https://github.com/o/r/pull/9", "--json", "baseRefName,body"),
                PreviewQuery.command("https://github.com/o/r/pull/9").executed().commandLine());
    }
}
