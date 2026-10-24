package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Loadable;
import org.junit.jupiter.api.Test;

/**
 * The rows of the panes of the pull requests screen.
 */
class PullRequestsPanelTest {

    private static PullRequestSummary pr(boolean draft) {
        return new PullRequestSummary(
                "owner/repo",
                12,
                "Fix the login",
                "alice",
                "fix/login",
                draft,
                false,
                Checks.FAILING,
                Review.APPROVED,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/12");
    }

    @Test
    void aRowShowsTheAgeTheNumberHowTheChecksAndReviewDoTheTitleAndTheAuthor() {
        assertEquals(
                "  2d #12 ❌ 👍 Fix the login alice",
                PullRequestsPanel.row(pr(false), Duration.ofDays(2), false).text());
    }

    @Test
    void aListOfAnyRepositoryNamesTheRepositoryOfEach() {
        assertEquals(
                "  2d repo#12 ❌ 👍 Fix the login alice",
                PullRequestsPanel.row(pr(false), Duration.ofDays(2), true).text());
    }

    @Test
    void theTitleSaysHowManyMatchWhenNotAllWereRead() {
        PullRequestList some = new PullRequestList(List.of(pr(false)), 52);

        assertEquals(
                "Review requested (1 of 52)", PullRequestsPanel.title("Review requested", new Loadable.Loaded<>(some)));
        assertEquals(
                "Review requested",
                PullRequestsPanel.title(
                        "Review requested", new Loadable.Loaded<>(new PullRequestList(List.of(pr(false)), 1))));
        assertEquals("Review requested", PullRequestsPanel.title("Review requested", Loadable.loading()));
    }

    @Test
    void theTopPaneGetsTheFocusTheFirstTime() {
        assertEquals("prRepository", PullRequestsPanel.FIRST_PANE_ID);
    }

    @Test
    void anUnseenPullRequestIsMarkedBeforeItsNumber() {
        LoadableList.Row unseen = PullRequestsPanel.row(pr(false), Duration.ofDays(2), false, Option.none(), true);
        LoadableList.Row seen = PullRequestsPanel.row(pr(false), Duration.ofDays(2), false, Option.none(), false);

        assertEquals(seen.text().replaceFirst(" #", "●#"), unseen.text());
        assertEquals(Option.of(Theme.Classes.UNSEEN), unseen.segs().get(1).cssClass());
    }

    @Test
    void aDraftIsDimmed() {
        List<Option<String>> classes = PullRequestsPanel.row(pr(true), Duration.ofDays(2), false)
                .segs()
                .map(LoadableList.Seg::cssClass);

        assertEquals(Option.of(Theme.Classes.DIM), classes.get(1));
        assertEquals(Option.of(Theme.Classes.DIM), classes.get(4));
        assertEquals(6, classes.size());
    }
}
