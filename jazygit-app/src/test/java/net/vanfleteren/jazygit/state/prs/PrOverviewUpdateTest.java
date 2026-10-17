package net.vanfleteren.jazygit.state.prs;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The pull requests screen: switching to it, and reading the pull requests it shows.
 */
class PrOverviewUpdateTest {

    static PullRequestSummary pr(int number, Instant updatedAt) {
        return new PullRequestSummary(
                "owner/repo",
                number,
                "Pull request " + number,
                "alice",
                "branch-" + number,
                false,
                false,
                Checks.NONE,
                Review.NONE,
                updatedAt,
                "https://github.com/owner/repo/pull/" + number);
    }

    private static final PullRequestSummary OLD = pr(1, Instant.parse("2026-10-01T00:00:00Z"));
    private static final PullRequestSummary NEW = pr(2, Instant.parse("2026-10-16T00:00:00Z"));

    private static Next toggled(Model model) {
        return Update.update(model, new PrOverviewMsg.Toggled());
    }

    @Test
    void theRepositoryScreenShowsFirstAndNothingIsReadForThePullRequestsUntilTheirsIsShown() {
        Model model = loaded();

        assertEquals(Screen.REPOSITORY, model.screen());
        assertEquals(
                List.empty(), Update.update(model, new PrOverviewMsg.Tick()).cmds());
    }

    @Test
    void showingThePullRequestsReadsThemAndShowingTheRepositoryAgainDoesNot() {
        Next shown = toggled(loaded());

        assertEquals(Screen.PULL_REQUESTS, shown.model().screen());
        assertEquals(List.of(new LoadRepositoryPullRequests()), shown.cmds());

        Next back = toggled(shown.model());
        assertEquals(Screen.REPOSITORY, back.model().screen());
        assertEquals(List.empty(), back.cmds());
    }

    @Test
    void aTickReadsThemAgainWhileShownUnlessAReadIsStillRunning() {
        Model shown = toggled(loaded()).model();

        assertEquals(
                List.empty(), Update.update(shown, new PrOverviewMsg.Tick()).cmds());

        Model read = Update.update(shown, new PrOverviewMsg.RepositoryLoaded(List.of(OLD)))
                .model();
        assertEquals(
                List.of(new LoadRepositoryPullRequests()),
                Update.update(read, new PrOverviewMsg.Tick()).cmds());
    }

    @Test
    void theMostRecentlyUpdatedComeFirst() {
        Model read = Update.update(toggled(loaded()).model(), new PrOverviewMsg.RepositoryLoaded(List.of(OLD, NEW)))
                .model();

        assertEquals(new Loadable.Loaded<>(List.of(NEW, OLD)), read.prOverview().repository());
        assertEquals(0, read.prOverview().reading());
    }

    @Test
    void failingToReadThemShowsWhyUntilTheyWereReadOnceAndThenKeepsTheOnesRead() {
        Model shown = toggled(loaded()).model();
        var failure = refused("gh: not logged in");

        assertInstanceOf(
                Loadable.Failed.class,
                Update.update(shown, new PrOverviewMsg.RepositoryLoadFailed(failure))
                        .model()
                        .prOverview()
                        .repository());

        Model read = Update.update(shown, new PrOverviewMsg.RepositoryLoaded(List.of(OLD)))
                .model();
        assertEquals(
                new Loadable.Loaded<>(List.of(OLD)),
                Update.update(read, new PrOverviewMsg.RepositoryLoadFailed(failure))
                        .model()
                        .prOverview()
                        .repository());
    }

    @Test
    void eachScreenKeepsWhereItsFocusWas() {
        Model files = Update.update(loaded(), new Msg.Focused("files")).model();
        Model prs = Update.update(toggled(files).model(), new Msg.Focused("prRepository"))
                .model();

        assertEquals(Option.of("prRepository"), prs.shownPane());
        Model back = toggled(prs).model();
        assertEquals(Option.of("files"), back.shownPane());
        assertEquals(Option.of("prRepository"), toggled(back).model().shownPane());
    }

    @Test
    void theHighlightedPullRequestIsKept() {
        assertEquals(
                Option.of(NEW),
                Update.update(loaded(), new PrOverviewMsg.Highlighted(NEW))
                        .model()
                        .prOverview()
                        .highlighted());
    }
}
