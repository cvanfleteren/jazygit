package net.vanfleteren.jazygit.gh.checks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

/**
 * The command that runs the failed jobs of a workflow run again (ADR 135).
 */
class RerunFailedJobsTest {

    @Test
    void theFailedJobsOfTheRunArePostedForThroughRest() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "POST",
                        "repos/{owner}/{repo}/actions/runs/19374512345/rerun-failed-jobs"),
                RerunFailedJobs.command(19374512345L).executed().commandLine());
    }
}
