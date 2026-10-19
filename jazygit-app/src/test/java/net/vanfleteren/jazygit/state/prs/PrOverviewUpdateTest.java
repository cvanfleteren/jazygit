package net.vanfleteren.jazygit.state.prs;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.state.AppError;
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

    static PullRequestSummary pr(String repository, int number, Instant updatedAt) {
        return new PullRequestSummary(
                repository,
                number,
                "Pull request " + number,
                "alice",
                "branch-" + number,
                false,
                false,
                Checks.NONE,
                Review.NONE,
                updatedAt,
                "https://github.com/" + repository + "/pull/" + number);
    }

    private static final PullRequestSummary OLD = pr("owner/repo", 1, Instant.parse("2026-10-01T00:00:00Z"));
    private static final PullRequestSummary NEW = pr("owner/repo", 2, Instant.parse("2026-10-16T00:00:00Z"));
    private static final PullRequestSummary ELSEWHERE = pr("other/repo", 3, Instant.parse("2026-10-10T00:00:00Z"));
    private static final List<Object> BOTH_READS = List.of(new LoadMyPullRequests(), new LoadRepositoryPullRequests());

    private static final Instant READ_AT = Instant.parse("2026-10-17T12:00:00Z");

    private static Next toggled(Model model) {
        return Update.update(model, new PrOverviewMsg.Toggled());
    }

    private static PrOverviewMsg repositoryRead(PullRequestSummary... pullRequests) {
        return new PrOverviewMsg.RepositoryLoaded(
                new RepositoryPullRequests(
                        "Owner/Repo", new PullRequestList(List.of(pullRequests), pullRequests.length)),
                READ_AT);
    }

    @Test
    void theRepositoryScreenShowsFirstAndNothingIsReadForThePullRequestsUntilTheirsIsShown() {
        Model model = loaded();

        assertEquals(Screen.REPOSITORY, model.screen());
        assertEquals(
                List.empty(), Update.update(model, new PrOverviewMsg.Tick()).cmds());
    }

    @Test
    void showingThePullRequestsReadsThemAllAndShowingTheRepositoryAgainDoesNot() {
        Next shown = toggled(loaded());

        assertEquals(Screen.PULL_REQUESTS, shown.model().screen());
        assertEquals(BOTH_READS, shown.cmds());

        Next back = toggled(shown.model());
        assertEquals(Screen.REPOSITORY, back.model().screen());
        assertEquals(List.empty(), back.cmds());
    }

    @Test
    void choosingAScreenShowsItAndChoosingTheOneShownDoesNothing() {
        Next shown = Update.update(loaded(), new PrOverviewMsg.Chosen(Screen.PULL_REQUESTS));
        assertEquals(Screen.PULL_REQUESTS, shown.model().screen());
        assertEquals(BOTH_READS, shown.cmds());

        Next again = Update.update(shown.model(), new PrOverviewMsg.Chosen(Screen.PULL_REQUESTS));
        assertEquals(shown.model(), again.model());
        assertEquals(List.empty(), again.cmds());

        Next back = Update.update(shown.model(), new PrOverviewMsg.Chosen(Screen.REPOSITORY));
        assertEquals(Screen.REPOSITORY, back.model().screen());
        assertEquals(List.empty(), back.cmds());
    }

    @Test
    void aTickReadsAgainWhatIsNotStillBeingRead() {
        Model shown = toggled(loaded()).model();

        assertEquals(
                List.empty(), Update.update(shown, new PrOverviewMsg.Tick()).cmds());

        Model read = Update.update(shown, repositoryRead(OLD)).model();
        assertEquals(
                List.of(new LoadRepositoryPullRequests()),
                Update.update(read, new PrOverviewMsg.Tick()).cmds());
    }

    @Test
    void theMostRecentlyUpdatedComeFirstAndTheRepositoryIsKnownOnceItsPullRequestsWereRead() {
        PrOverview read = Update.update(toggled(loaded()).model(), repositoryRead(OLD, NEW))
                .model()
                .prOverview();

        assertEquals(new Loadable.Loaded<>(new PullRequestList(List.of(NEW, OLD), 2)), read.repository());
        assertEquals(HashSet.of(new LoadMyPullRequests()), read.reading());
        assertEquals(Either.right(true), read.ofThisRepository(NEW));
        assertEquals(Either.right(false), read.ofThisRepository(ELSEWHERE));
    }

    @Test
    void whetherAPullRequestIsOfTheRepositoryIsNotKnownWhileItsPullRequestsAreReadOrWhenThatFailed() {
        var failure = refused("gh: not logged in");

        assertEquals(Either.left(new AppError.PullRequestsNotRead()), PrOverview.INITIAL.ofThisRepository(NEW));
        assertEquals(
                Either.left(new AppError.PullRequestsUnavailable(failure)),
                PrOverview.INITIAL
                        .withRepository(new Loadable.Failed<>(failure))
                        .ofThisRepository(NEW));
    }

    @Test
    void myPullRequestsFillBothListsWithHowManyMatch() {
        PrOverview read = Update.update(
                        toggled(loaded()).model(),
                        new PrOverviewMsg.MineLoaded(
                                new MyPullRequests(
                                        new PullRequestList(List.of(ELSEWHERE), 52),
                                        new PullRequestList(List.of(NEW), 1)),
                                READ_AT))
                .model()
                .prOverview();

        assertEquals(new Loadable.Loaded<>(new PullRequestList(List.of(ELSEWHERE), 52)), read.reviewRequested());
        assertEquals(new Loadable.Loaded<>(new PullRequestList(List.of(NEW), 1)), read.authored());
    }

    @Test
    void failingToReadThemShowsWhyUntilTheyWereReadOnceAndThenKeepsTheOnesRead() {
        Model shown = toggled(loaded()).model();
        var failure = refused("gh: not logged in");

        PrOverview failed = Update.update(shown, new PrOverviewMsg.MineLoadFailed(failure))
                .model()
                .prOverview();
        assertInstanceOf(Loadable.Failed.class, failed.reviewRequested());
        assertInstanceOf(Loadable.Failed.class, failed.authored());

        Model read = Update.update(shown, repositoryRead(OLD)).model();
        assertEquals(
                read.prOverview().repository(),
                Update.update(read, new PrOverviewMsg.RepositoryLoadFailed(failure))
                        .model()
                        .prOverview()
                        .repository());
    }

    @Test
    void theContentKeepsTheListWhosePullRequestItShows() {
        Model authored = Update.update(toggled(loaded()).model(), new Msg.Focused("prAuthored", false))
                .model();
        Model inContent = Update.update(authored, new Msg.Focused("pull-request-preview", true))
                .model();

        assertEquals(Option.of("pull-request-preview"), inContent.shownPane());
        assertEquals(Option.of("prAuthored"), inContent.prOverview().listPane());
    }

    @Test
    void eachScreenKeepsWhereItsFocusWas() {
        Model files = Update.update(loaded(), new Msg.Focused("files", false)).model();
        Model prs = Update.update(toggled(files).model(), new Msg.Focused("prAuthored", false))
                .model();

        assertEquals(Option.of("prAuthored"), prs.shownPane());
        Model back = toggled(prs).model();
        assertEquals(Option.of("files"), back.shownPane());
        assertEquals(Option.of("prAuthored"), toggled(back).model().shownPane());
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

    @Test
    void highlightingAPullRequestReadsItsPreviewOnceUntilItChanges() {
        Next highlighted = Update.update(loaded(), new PrOverviewMsg.Highlighted(NEW));

        assertEquals(List.of(new LoadPullRequestPreview(NEW)), highlighted.cmds());
        assertEquals(
                List.empty(),
                Update.update(highlighted.model(), new PrOverviewMsg.Highlighted(NEW))
                        .cmds());

        PullRequestSummary changed = pr("owner/repo", 2, Instant.parse("2026-10-17T00:00:00Z"));
        assertEquals(
                List.of(new LoadPullRequestPreview(changed)),
                Update.update(highlighted.model(), new PrOverviewMsg.Highlighted(changed))
                        .cmds());
    }

    @Test
    void theReadPreviewIsShownAndTheOneBeforeStaysWhileTheNextIsRead() {
        PullRequestPreview preview = new PullRequestPreview("main", "Body", "+a\n", false);
        Model read = Update.update(
                        Update.update(loaded(), new PrOverviewMsg.Highlighted(NEW))
                                .model(),
                        new PrOverviewMsg.PreviewLoaded(NEW, preview))
                .model();

        assertEquals(
                new Loadable.Loaded<>(preview),
                read.prOverview().preview().get().value());

        Model next = Update.update(read, new PrOverviewMsg.Highlighted(OLD)).model();
        assertEquals(NEW, next.prOverview().preview().get().shown().key());
    }

    @Test
    void refreshingReadsAPreviewThatFailedAgain() {
        Model failed = Update.update(
                        Update.update(loaded(), new PrOverviewMsg.Highlighted(NEW))
                                .model(),
                        new PrOverviewMsg.PreviewLoadFailed(NEW, refused("offline")))
                .model();

        assertTrue(Update.update(failed, new PrOverviewMsg.Refresh()).cmds().contains(new LoadPullRequestPreview(NEW)));
    }

    @Test
    void theFreshnessSaysWhenTheyWereReadAndWhichReadFailedSince() {
        Model read =
                Update.update(toggled(loaded()).model(), repositoryRead(OLD)).model();
        assertEquals(Option.of(READ_AT), read.prOverview().freshness().read());
        assertFalse(read.prOverview().freshness().stale());

        Model failed = Update.update(read, new PrOverviewMsg.MineLoadFailed(refused("offline")))
                .model();
        assertEquals(Option.of(READ_AT), failed.prOverview().freshness().read());
        assertTrue(failed.prOverview().freshness().stale());
    }
}
