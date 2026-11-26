package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.github.checks.timings.CheckTimesCmd;
import net.vanfleteren.jazygit.features.github.checks.timings.LoadBranchCheckRuns;
import net.vanfleteren.jazygit.features.github.prs.LoadMyPullRequests;
import net.vanfleteren.jazygit.features.github.prs.LoadPullRequestPreview;
import net.vanfleteren.jazygit.features.github.remarks.RemarksCmd;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettingsCmd;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd;
import org.junit.jupiter.api.Test;

/**
 * What only reads from GitHub runs in the background, next to the other commands: it is slow, and the user is not
 * waiting for it.
 */
class BackgroundCmdTest {

    private static final PullRequestSummary PULL_REQUEST = new PullRequestSummary(
            new RepoSlug("owner/repo"),
            new PrNumber(7),
            "Pull request 7",
            "alice",
            new ForgeBranch("feature"),
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
                new PullRequestCmd.CheckMerge(new PrNumber(7), "PR_1"),
                new LoadBranchCheckRuns(List.empty()),
                new RemarksCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 0),
                new CheckTimesCmd.TopUpCheckTimes(new RepoSlug("owner/repo"), Instant.EPOCH),
                new CheckTimesCmd.RecordCheckTimes(new RepoSlug("owner/repo"), List.empty(), Instant.EPOCH, false),
                new GitHubSettingsCmd.CheckGhExecutable(Option.none()),
                new GitHubSettingsCmd.ReadGitHub("gh"));

        for (Cmd read : reads) {
            assertTrue(read.background(), () -> read.getClass().getSimpleName());
        }
    }
}
