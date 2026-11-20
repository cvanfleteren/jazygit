package net.vanfleteren.jazygit.core.gh.checks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.process.Limited;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;

/**
 * The command that reads the file of a workflow at a commit from GitHub (ADR 138).
 */
class WorkflowFileQueryTest {

    @Test
    void theFileIsReadAsItIsAtTheCommitThroughRest() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "-H",
                        "Accept: application/vnd.github.raw",
                        "repos/{owner}/{repo}/contents/.github/workflows/build.yml"
                                + "?ref=868abc7105503ff12f23c6dfbad4e8cd9089c419"),
                WorkflowFileQuery.command(
                                new Sha("868abc7105503ff12f23c6dfbad4e8cd9089c419"), ".github/workflows/build.yml")
                        .executed()
                        .commandLine());
    }

    @Test
    void aFileReadWholeIsItsText() {
        assertEquals(Either.right("jobs: {}\n"), WorkflowFileQuery.text(new Limited("jobs: {}\n", false)));
    }

    @Test
    void aFileCutOffIsTooMuchOutputOfApi() {
        assertEquals(
                Either.left(new GhError.TooMuchOutput("api")), WorkflowFileQuery.text(new Limited("jobs:\n", true)));
    }

    @Test
    void aPathIsEncodedSegmentBySegment() {
        assertEquals(
                "repos/{owner}/{repo}/contents/.github/workflows/my%20build%23.yml?ref=abc",
                WorkflowFileQuery.command(new Sha("abc"), ".github/workflows/my build#.yml")
                        .executed()
                        .commandLine()
                        .last());
    }
}
