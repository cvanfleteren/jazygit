package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import org.junit.jupiter.api.Test;

/**
 * The tests of ShownClocksTest with GitHub in the app (ADR 171).
 */
class ShownClocksGitHubTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    /**
     * The repository, fetched {@code seconds} before NOW: how long ago changes sooner than the ages of its branches,
     * which are days.
     */
    private static Model fetched(int seconds) {
        return TestModels.loaded()
                .withNow(NOW)
                .mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", NOW.minusSeconds(seconds))));
    }

    @Test
    void theTimesOfAScreenWakeTheAppOnlyWhileItIsShown() {
        // A pull request updated 3m 20s before NOW: 3m until 40s later (ADR 168).
        PullRequestSummary updated = new PullRequestSummary(
                new RepoSlug("owner/repo"),
                new PrNumber(7),
                "Pull request 7",
                "alice",
                new ForgeBranch("feature"),
                false,
                false,
                Checks.NONE,
                Review.NONE,
                NOW.minusSeconds(3 * 60 + 20),
                "https://github.com/owner/repo/pull/7");
        Model listed = TestModels.loaded()
                .withNow(NOW)
                .mapRepoView(view -> view.withBranchesTab(BranchesTab.WORKTREES))
                .withPart(
                        PrOverview.PART,
                        PrOverview.INITIAL.withAuthored(
                                new Loadable.Loaded<>(new PullRequestList(List.of(updated), 1))));

        assertEquals(Option.none(), ShownClocks.next(listed, NOW));
        assertEquals(Option.of(NOW.plusSeconds(40)), ShownClocks.next(listed.withScreen(PrOverview.SCREEN), NOW));
    }
}
