package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
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
                "  2d #12 ✗ ✔ Fix the login alice",
                PullRequestsPanel.row(pr(false), Duration.ofDays(2)).text());
    }

    @Test
    void aDraftIsDimmed() {
        List<Option<String>> classes =
                PullRequestsPanel.row(pr(true), Duration.ofDays(2)).segs().map(LoadableList.Seg::cssClass);

        assertEquals(Option.of(Theme.Classes.DIM), classes.get(1));
        assertEquals(Option.of(Theme.Classes.DIM), classes.get(4));
    }
}
