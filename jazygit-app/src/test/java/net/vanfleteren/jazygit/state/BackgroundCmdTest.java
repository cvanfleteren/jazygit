package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.diff.CommitFilesCmd;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.feature.settings.SettingsPanelCmd;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.github.CheckTimesCmd;
import net.vanfleteren.jazygit.state.github.LoadBranchCheckRuns;
import net.vanfleteren.jazygit.state.prs.LoadMyPullRequests;
import net.vanfleteren.jazygit.state.prs.LoadPullRequestPreview;
import org.junit.jupiter.api.Test;

/**
 * What only reads from GitHub runs in the background, next to the other commands: it is slow, and the user is not
 * waiting for it.
 */
class BackgroundCmdTest {

    private static final PullRequestSummary PULL_REQUEST = new PullRequestSummary(
            "owner/repo",
            7,
            "Pull request 7",
            "alice",
            "feature",
            false,
            false,
            Checks.NONE,
            Review.NONE,
            Instant.EPOCH,
            "https://github.com/owner/repo/pull/7");

    @Test
    void readingFromGitHubRunsInTheBackground() {
        List<Cmd> reads = List.of(
                new LoadMyPullRequests(),
                new LoadPullRequestPreview(PULL_REQUEST),
                new PullRequestCmd.CheckMerge(7, "PR_1"),
                new LoadBranchCheckRuns(List.empty()),
                new CommitFilesCmd.LoadReviewRemarks(7, HashSet.empty(), 0),
                new CheckTimesCmd.TopUpCheckTimes("owner/repo", Instant.EPOCH),
                new CheckTimesCmd.RecordCheckTimes("owner/repo", List.empty(), Instant.EPOCH, false),
                new SettingsPanelCmd.CheckGhExecutable(Option.none()),
                new SettingsPanelCmd.ReadGitHub("gh"));

        for (Cmd read : reads) {
            assertTrue(read.background(), () -> read.getClass().getSimpleName());
        }
    }
}
