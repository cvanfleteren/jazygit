package net.vanfleteren.jazygit.features.github.prs;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.collection.TreeSet;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.model.RepositoryPullRequests;
import net.vanfleteren.jazygit.features.github.prs.content.PullRequestSource;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.PullRequestsError;
import net.vanfleteren.jazygit.features.github.state.UnseenPullRequests;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import org.junit.jupiter.api.Test;

/**
 * The pull requests screen: switching to it, reading the pull requests it shows, and which of them were opened and
 * not seen yet.
 */
class PrOverviewUpdateTest {

    static PullRequestSummary pr(String repository, int number, Instant updatedAt) {
        return new PullRequestSummary(
                repository,
                new PrNumber(number),
                "Pull request " + number,
                "alice",
                new GhBranch("branch-" + number),
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

    /**
     * What the pull requests screen answers, as GitHub's feature declares it.
     */
    private static final Reactions SCREEN_SHOWN = new Reactions(List.of(PrOverviewUpdate::to));

    private static Next toggled(Model model) {
        return Update.update(model.withReactions(SCREEN_SHOWN), new ScreenMsg.Toggled(PrOverview.SCREEN));
    }

    private static PrOverviewMsg repositoryRead(PullRequestSummary... pullRequests) {
        return repositoryRead(Option.none(), Option.none(), pullRequests);
    }

    /**
     * The pull requests of the repository read as {@code viewer}, with {@code kept} kept for it.
     */
    private static PrOverviewMsg repositoryRead(
            Option<String> viewer, Option<UnseenPullRequests> kept, PullRequestSummary... pullRequests) {
        return repositoryRead(viewer, kept, 0, pullRequests);
    }

    /**
     * The pull requests of the repository read as {@code viewer}, with {@code kept} kept for it, and {@code latest}
     * the number of the one opened last.
     */
    private static PrOverviewMsg repositoryRead(
            Option<String> viewer, Option<UnseenPullRequests> kept, int latest, PullRequestSummary... pullRequests) {
        return new PrOverviewMsg.RepositoryLoaded(
                new RepositoryPullRequests(
                        "Owner/Repo",
                        new PullRequestList(List.of(pullRequests), pullRequests.length),
                        viewer,
                        Option.of(latest).filter(number -> number > 0).map(PrNumber::new)),
                kept);
    }

    private static UnseenPullRequests unseen(int highest, Integer... numbers) {
        return new UnseenPullRequests(highest, TreeSet.of(numbers));
    }

    /**
     * {@code model} after the pull requests of the repository were read, as the user {@code bob}, once with
     * {@code first} and then with {@code then}.
     */
    private static Next readTwice(Model model, List<PullRequestSummary> first, List<PullRequestSummary> then) {
        Model once = Update.update(
                        model,
                        repositoryRead(Option.of("bob"), Option.none(), first.toJavaArray(PullRequestSummary[]::new)))
                .model();
        return Update.update(
                once, repositoryRead(Option.of("bob"), Option.none(), then.toJavaArray(PullRequestSummary[]::new)));
    }

    private static PullRequestSummary by(String author, PullRequestSummary pr) {
        return new PullRequestSummary(
                pr.repository(),
                pr.number(),
                pr.title(),
                author,
                pr.headBranch(),
                pr.draft(),
                pr.fromFork(),
                pr.checks(),
                pr.review(),
                pr.updatedAt(),
                pr.url());
    }

    @Test
    void theRepositoryScreenShowsFirstAndATickOnItReadsNothing() {
        // The pull requests of the repository are read after every fetch, for the branches and the new ones (ADR 58).
        Model model = loaded();

        assertEquals(Screen.REPOSITORY, model.screen());
        assertEquals(
                List.empty(), Update.update(model, new PrOverviewMsg.Tick()).cmds());
    }

    @Test
    void thePullRequestsOfTheRepositoryAreThoseOfTheBranchesToo() {
        Model read = Update.update(loaded(), repositoryRead(OLD)).model();

        assertEquals(
                List.of(OLD.number()),
                read.part(GitHubState.PART)
                        .pullRequests()
                        .get()
                        .values()
                        .map(pr -> pr.number())
                        .toList());
    }

    @Test
    void theFirstReadOfTheRepositoryHasNoneUnseenAndKeepsTheHighestNumber() {
        Next read = Update.update(loaded(), repositoryRead(NEW, OLD));

        assertEquals(Option.of(unseen(2)), read.model().part(PrOverview.PART).unseen());
        assertEquals(0, read.model().part(PrOverview.PART).unseenCount());
        assertEquals(List.of(new SaveUnseenPullRequests("Owner/Repo", unseen(2))), read.cmds());
    }

    @Test
    void oneAboveTheHighestNumberReadIsUnseenButNotOneTheUserOpened() {
        PullRequestSummary third = pr("owner/repo", 3, Instant.parse("2026-10-17T00:00:00Z"));
        PullRequestSummary fourth = by("Bob", pr("owner/repo", 4, Instant.parse("2026-10-17T00:00:00Z")));

        Next read = readTwice(loaded(), List.of(OLD), List.of(fourth, third, OLD));

        assertEquals(Option.of(unseen(4, 3)), read.model().part(PrOverview.PART).unseen());
        assertEquals(1, read.model().part(PrOverview.PART).unseenCount());
        assertTrue(PrOverview.isUnseen(read.model(), third));
        assertFalse(PrOverview.isUnseen(read.model(), fourth));
        assertEquals(List.of(new SaveUnseenPullRequests("Owner/Repo", unseen(4, 3))), read.cmds());
    }

    @Test
    void anOldPullRequestThatIsReadForTheFirstTimeIsNotUnseen() {
        Next read = readTwice(loaded(), List.of(NEW), List.of(NEW, OLD));

        assertEquals(Option.of(unseen(2)), read.model().part(PrOverview.PART).unseen());
        assertEquals(List.empty(), read.cmds());
    }

    @Test
    void theHighestNumberIsTheOneOfThePullRequestOpenedLastSoAReopenedOneIsNotNew() {
        PullRequestSummary reopened = pr("owner/repo", 12, Instant.parse("2026-10-17T00:00:00Z"));

        Model none = Update.update(loaded(), repositoryRead(Option.none(), Option.none(), 45))
                .model();
        assertEquals(Option.of(unseen(45)), none.part(PrOverview.PART).unseen());

        Next read = Update.update(none, repositoryRead(Option.none(), Option.none(), 45, reopened));
        assertEquals(Option.of(unseen(45)), read.model().part(PrOverview.PART).unseen());
        assertEquals(List.empty(), read.cmds());
    }

    @Test
    void oneOfTheNewestThatWereMergedBeforeTheFirstReadIsNotNewWhenReopened() {
        PullRequestSummary fortieth = pr("owner/repo", 40, Instant.parse("2026-10-16T00:00:00Z"));
        PullRequestSummary reopened = pr("owner/repo", 43, Instant.parse("2026-10-17T00:00:00Z"));
        PullRequestSummary opened = pr("owner/repo", 46, Instant.parse("2026-10-17T00:00:00Z"));

        Model first = Update.update(loaded(), repositoryRead(Option.none(), Option.none(), 45, fortieth))
                .model();
        Model again = Update.update(first, repositoryRead(Option.none(), Option.none(), 46, opened, reopened, fortieth))
                .model();

        assertEquals(Option.of(unseen(46, 46)), again.part(PrOverview.PART).unseen());
        assertFalse(PrOverview.isUnseen(again, reopened));
    }

    @Test
    void whatWasKeptCountsUntilTheModelHasItsOwn() {
        Next read = Update.update(loaded(), repositoryRead(Option.none(), Option.of(unseen(1)), NEW, OLD));

        assertEquals(Option.of(unseen(2, 2)), read.model().part(PrOverview.PART).unseen());

        Model own = read.model();
        assertEquals(
                Option.of(unseen(2, 2)),
                Update.update(own, repositoryRead(Option.none(), Option.of(unseen(5)), NEW, OLD))
                        .model()
                        .part(PrOverview.PART)
                        .unseen());
    }

    @Test
    void oneThatIsNotOpenAnymoreIsNotUnseenAnymore() {
        Model unseenTwo = Update.update(loaded(), repositoryRead(Option.none(), Option.of(unseen(1)), NEW, OLD))
                .model();

        Next closed = Update.update(unseenTwo, repositoryRead(OLD));

        assertEquals(Option.of(unseen(2)), closed.model().part(PrOverview.PART).unseen());
        assertEquals(List.of(new SaveUnseenPullRequests("Owner/Repo", unseen(2))), closed.cmds());
    }

    @Test
    void highlightingAnUnseenPullRequestOfTheRepositorySeesItAndKeepsThat() {
        Model unseenTwo = Update.update(loaded(), repositoryRead(Option.none(), Option.of(unseen(1)), NEW, OLD))
                .model();

        Next elsewhere = Update.update(unseenTwo, new PrOverviewMsg.Highlighted(pr("other/repo", 2, NEW.updatedAt())));
        assertEquals(1, elsewhere.model().part(PrOverview.PART).unseenCount());

        Next seen = Update.update(unseenTwo, new PrOverviewMsg.Highlighted(NEW));
        assertEquals(Option.of(unseen(2)), seen.model().part(PrOverview.PART).unseen());
        assertEquals(
                List.of(new LoadPullRequestPreview(NEW), new SaveUnseenPullRequests("Owner/Repo", unseen(2))),
                seen.cmds());
        assertEquals(
                List.empty(),
                Update.update(seen.model(), new PrOverviewMsg.Highlighted(NEW)).cmds());
    }

    @Test
    void aFailedReadKeepsTheUnseenPullRequests() {
        Model unseenTwo = Update.update(loaded(), repositoryRead(Option.none(), Option.of(unseen(1)), NEW, OLD))
                .model();

        assertEquals(
                Option.of(unseen(2, 2)),
                Update.update(unseenTwo, new PrOverviewMsg.RepositoryLoadFailed(refused("offline")))
                        .model()
                        .part(PrOverview.PART)
                        .unseen());
    }

    @Test
    void showingThePullRequestsReadsThemAllAndShowingTheRepositoryAgainDoesNot() {
        Next shown = toggled(loaded());

        assertEquals(PrOverview.SCREEN, shown.model().screen());
        assertEquals(BOTH_READS, shown.cmds());

        Next back = toggled(shown.model());
        assertEquals(Screen.REPOSITORY, back.model().screen());
        assertEquals(List.empty(), back.cmds());
    }

    @Test
    void choosingAScreenShowsItAndChoosingTheOneShownDoesNothing() {
        Next shown = Update.update(loaded().withReactions(SCREEN_SHOWN), new ScreenMsg.Shown(PrOverview.SCREEN));
        assertEquals(PrOverview.SCREEN, shown.model().screen());
        assertEquals(BOTH_READS, shown.cmds());

        Next again = Update.update(shown.model(), new ScreenMsg.Shown(PrOverview.SCREEN));
        assertEquals(shown.model(), again.model());
        assertEquals(List.empty(), again.cmds());

        Next back = Update.update(shown.model(), new ScreenMsg.Shown(Screen.REPOSITORY));
        assertEquals(Screen.REPOSITORY, back.model().screen());
        assertEquals(List.empty(), back.cmds());
    }

    @Test
    void aTickReadsAgainWhatIsNotStillBeingRead() {
        Model shown = TestModels.started(toggled(loaded())).model();

        assertEquals(
                List.empty(), Update.update(shown, new PrOverviewMsg.Tick()).cmds());

        Model read = TestModels.done(
                        shown,
                        new LoadMyPullRequests(),
                        new PrOverviewMsg.MineLoaded(new MyPullRequests(PullRequestList.EMPTY, PullRequestList.EMPTY)))
                .model();
        assertEquals(
                List.of(new LoadMyPullRequests()),
                Update.update(read, new PrOverviewMsg.Tick()).cmds());
    }

    @Test
    void theMostRecentlyUpdatedComeFirstAndTheRepositoryIsKnownOnceItsPullRequestsWereRead() {
        Model model = TestModels.done(
                        TestModels.started(toggled(loaded())).model(),
                        new LoadRepositoryPullRequests(),
                        repositoryRead(OLD, NEW))
                .model();
        PrOverview read = model.part(PrOverview.PART);

        assertEquals(new Loadable.Loaded<>(new PullRequestList(List.of(NEW, OLD), 2)), read.repository());
        assertTrue(model.jobs().runs(new LoadMyPullRequests()));
        assertFalse(model.jobs().runs(new LoadRepositoryPullRequests()));
        assertEquals(Either.right(true), PrOverview.ofThisRepository(model, NEW));
        assertEquals(Either.right(false), PrOverview.ofThisRepository(model, ELSEWHERE));
    }

    @Test
    void whetherAPullRequestIsOfTheRepositoryIsNotKnownWhileItsPullRequestsAreReadOrWhenThatFailed() {
        var failure = refused("gh: not logged in");

        assertEquals(
                Either.left(new PullRequestsError.NotRead()), PrOverview.INITIAL.ofThisRepository(Option.none(), NEW));
        assertEquals(
                Either.left(new PullRequestsError.Unavailable(failure)),
                PrOverview.INITIAL
                        .withRepository(new Loadable.Failed<>(failure))
                        .ofThisRepository(Option.none(), NEW));
    }

    @Test
    void myPullRequestsFillBothListsWithHowManyMatch() {
        PrOverview read = Update.update(
                        toggled(loaded()).model(),
                        new PrOverviewMsg.MineLoaded(new MyPullRequests(
                                new PullRequestList(List.of(ELSEWHERE), 52), new PullRequestList(List.of(NEW), 1))))
                .model()
                .part(PrOverview.PART);

        assertEquals(new Loadable.Loaded<>(new PullRequestList(List.of(ELSEWHERE), 52)), read.reviewRequested());
        assertEquals(new Loadable.Loaded<>(new PullRequestList(List.of(NEW), 1)), read.authored());
    }

    @Test
    void failingToReadThemShowsWhyUntilTheyWereReadOnceAndThenKeepsTheOnesRead() {
        Model shown = toggled(loaded()).model();
        var failure = refused("gh: not logged in");

        PrOverview failed = Update.update(shown, new PrOverviewMsg.MineLoadFailed(failure))
                .model()
                .part(PrOverview.PART);
        assertInstanceOf(Loadable.Failed.class, failed.reviewRequested());
        assertInstanceOf(Loadable.Failed.class, failed.authored());

        Model read = Update.update(shown, repositoryRead(OLD)).model();
        assertEquals(
                read.part(PrOverview.PART).repository(),
                Update.update(read, new PrOverviewMsg.RepositoryLoadFailed(failure))
                        .model()
                        .part(PrOverview.PART)
                        .repository());
    }

    @Test
    void theContentKeepsTheListWhosePullRequestItShows() {
        Model authored = Update.update(toggled(loaded()).model(), new ViewMsg.Focused(new PaneId("prAuthored")))
                .model();
        Model inContent = Update.update(authored, new ViewMsg.Focused(new AreaId("pull-request-preview")))
                .model();

        assertEquals(Option.of(PullRequestSource.AREA.id()), inContent.shownPane());
        assertEquals(Option.of(new PaneId("prAuthored")), inContent.paneInUse(PrOverview.SCREEN));
    }

    @Test
    void eachScreenKeepsWhereItsFocusWas() {
        Model files = Update.update(loaded(), new ViewMsg.Focused(new PaneId("files")))
                .model();
        Model prs = Update.update(toggled(files).model(), new ViewMsg.Focused(new PaneId("prAuthored")))
                .model();

        assertEquals(Option.of(new PaneId("prAuthored")), prs.shownPane());
        Model back = toggled(prs).model();
        assertEquals(Option.of(new PaneId("files")), back.shownPane());
        assertEquals(Option.of(new PaneId("prAuthored")), toggled(back).model().shownPane());
    }

    @Test
    void theHighlightedPullRequestIsKept() {
        assertEquals(
                Option.of(NEW),
                Update.update(loaded(), new PrOverviewMsg.Highlighted(NEW))
                        .model()
                        .part(PrOverview.PART)
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
        PullRequestPreview preview = new PullRequestPreview(new GhBranch("main"), "Body", "+a\n", false);
        Model read = Update.update(
                        Update.update(loaded(), new PrOverviewMsg.Highlighted(NEW))
                                .model(),
                        new PrOverviewMsg.PreviewLoaded(NEW, preview))
                .model();

        assertEquals(
                new Loadable.Loaded<>(preview),
                read.part(PrOverview.PART).preview().get().value());

        Model next = Update.update(read, new PrOverviewMsg.Highlighted(OLD)).model();
        assertEquals(NEW, next.part(PrOverview.PART).preview().get().shown().key());
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
    void theFreshnessSaysWhenByTheClockTheyWereReadAndWhichReadFailedSince() {
        Model read = Update.update(toggled(loaded()).model().withNow(READ_AT), repositoryRead(OLD))
                .model();
        assertEquals(Option.of(READ_AT), read.part(PrOverview.PART).freshness().read());
        assertFalse(read.part(PrOverview.PART).freshness().stale());

        Model failed = Update.update(read, new PrOverviewMsg.MineLoadFailed(refused("offline")))
                .model();
        assertEquals(
                Option.of(READ_AT), failed.part(PrOverview.PART).freshness().read());
        assertTrue(failed.part(PrOverview.PART).freshness().stale());
    }
}
