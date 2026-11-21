package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TerminalMsg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import org.junit.jupiter.api.Test;

class ShownClocksTest {

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
    void theAppIsWokenWhenTheFirstTimeShownChanges() {
        assertEquals(Option.of(NOW.plusSeconds(2)), ShownClocks.next(fetched(28), NOW));
    }

    @Test
    void withNoTimeShownTheAppIsNotWoken() {
        // The worktrees show no age, and nothing was fetched yet.
        Model worktrees = TestModels.loaded()
                .withNow(NOW)
                .mapRepoView(view -> view.mapBranches(branches -> branches.withTab(BranchesTab.WORKTREES)));

        assertEquals(Option.none(), ShownClocks.next(worktrees, NOW));
    }

    @Test
    void theTimesOfAScreenWakeTheAppOnlyWhileItIsShown() {
        // A pull request updated 3m 20s before NOW: 3m until 40s later (ADR 168).
        PullRequestSummary updated = new PullRequestSummary(
                "owner/repo",
                new PrNumber(7),
                "Pull request 7",
                "alice",
                new GhBranch("feature"),
                false,
                false,
                Checks.NONE,
                Review.NONE,
                NOW.minusSeconds(3 * 60 + 20),
                "https://github.com/owner/repo/pull/7");
        Model listed = TestModels.loaded()
                .withNow(NOW)
                .mapRepoView(view -> view.mapBranches(branches -> branches.withTab(BranchesTab.WORKTREES)))
                .withPrOverview(PrOverview.INITIAL.withAuthored(
                        new Loadable.Loaded<>(new PullRequestList(List.of(updated), 1))));

        assertEquals(Option.none(), ShownClocks.next(listed, NOW));
        assertEquals(Option.of(NOW.plusSeconds(40)), ShownClocks.next(listed.withScreen(PrOverview.SCREEN), NOW));
    }

    @Test
    void whileAwayATimeShownMovesOnNoSoonerThanEveryFiveSeconds() {
        Model away = Update.update(fetched(28), new TerminalMsg.Focused(false)).model();
        Model later = Update.update(fetched(13), new TerminalMsg.Focused(false)).model();

        assertEquals(Option.of(NOW.plus(Model.AWAY_CLOCK)), ShownClocks.next(away, NOW));
        assertEquals(Option.of(NOW.plusSeconds(7)), ShownClocks.next(later, NOW));
    }
}
