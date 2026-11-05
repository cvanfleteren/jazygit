package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.process.Limit;
import net.vanfleteren.jazygit.process.Limited;
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
                PreviewQuery.preview("main", "Fixes the login.\n\nSee #3.\n", new Limited("+a\n", false)));
        assertEquals(
                new PullRequestPreview("main", "", "", false),
                PreviewQuery.preview("main", "", new Limited("", false)));
    }

    @Test
    void aDiffCutOffWhenItWasReadIsTruncated() {
        assertEquals(
                new PullRequestPreview("main", "", "+a\n", true),
                PreviewQuery.preview("main", "", new Limited("+a\n", true)));
    }

    @Test
    void theDiffIsReadUpToWhatGitsDiffsAre() {
        assertEquals(new Limit(50_000, 4 * 1024 * 1024), PreviewQuery.DIFF_LIMIT);
    }

    @Test
    void thePreviewIsAskedWithoutJq() {
        assertEquals(
                List.of("gh", "pr", "view", "https://github.com/o/r/pull/9", "--json", "baseRefName,body"),
                PreviewQuery.command("https://github.com/o/r/pull/9").executed().commandLine());
    }
}
