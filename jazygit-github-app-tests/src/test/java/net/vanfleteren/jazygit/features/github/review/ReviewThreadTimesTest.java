package net.vanfleteren.jazygit.features.github.review;

import static net.vanfleteren.jazygit.features.github.review.ReviewFixtures.HEAD;
import static net.vanfleteren.jazygit.features.github.review.ReviewFixtures.files;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.kernel.content.ContentView;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import org.junit.jupiter.api.Test;

/**
 * The times the review threads of a pull request show in its diff (ADR 124).
 */
class ReviewThreadTimesTest {

    @Test
    void theAgesOfTheCommentsInADiffMoveOnWithTheMinuteOfTheClock() {
        Instant now = Instant.parse("2026-11-14T10:00:20Z");

        assertEquals(
                Option.of(Instant.parse("2026-11-14T10:01:00Z")), ShownTime.next(ContentView.shownTimes(files()), now));
    }

    @Test
    void withoutThreadsNoTimeIsShown() {
        assertEquals(List.empty(), ContentView.shownTimes(files(PullRequestDetails.State.OPEN, HEAD, List.empty())));
        assertEquals(List.empty(), ContentView.shownTimes(TestModels.loaded()));
    }
}
