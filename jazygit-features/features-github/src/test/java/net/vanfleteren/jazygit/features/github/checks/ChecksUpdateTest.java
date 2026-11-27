package net.vanfleteren.jazygit.features.github.checks;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.timing.CheckSample;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.checks.Reading;
import net.vanfleteren.jazygit.features.github.checks.branches.BranchChecksMsg;
import net.vanfleteren.jazygit.features.github.checks.branches.LoadBranchChecks;
import net.vanfleteren.jazygit.features.github.checks.timings.CheckTimesCmd;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.features.github.state.GitHubChecks;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.PendingChecks;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * Showing the checks on the tip of a branch one by one, and reading them again (ADR 32, ADR 34).
 */
class ChecksUpdateTest {

    private static final RemoteBranch FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final BranchRef MINE = new BranchRef.Local(new BranchName("mine"));
    private static final Check PASSED = new Check(
            "package",
            Checks.PASSING,
            Option.of(new CheckRun(
                    1,
                    "Build",
                    "SUCCESS",
                    Option.of(Instant.EPOCH.plusSeconds(2)),
                    Option.of(Instant.EPOCH.plusSeconds(60)),
                    1,
                    Instant.EPOCH,
                    1,
                    "push")));
    private static final PendingChecks READ =
            new PendingChecks(new RepoSlug("owner/repo"), HashMap.of(new BranchName("feature"), List.of(PASSED)));
    private static final PendingChecks RUNNING = new PendingChecks(
            new RepoSlug("owner/repo"),
            HashMap.of(new BranchName("feature"), List.of(PASSED, new Check("ci/status", Checks.PENDING))));

    /**
     * The sample model with a local branch {@code mine} that follows {@code origin/feature}, whose checks are
     * {@code checks} on GitHub.
     */
    private static Model model(Checks checks) {
        Branch mine = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(FEATURE), 0, 0)));
        Model model = TestModels.localBranchesRead(loaded(), List.of(mine)).model();
        return Update.update(
                        model,
                        new BranchChecksMsg.Loaded(new GitHubChecks(
                                HashSet.of(new RemoteName("origin")), HashMap.of(new BranchName("feature"), checks))))
                .model();
    }

    // The pull request from feature, shown in its dialog once its details are read.
    private static final PullRequest FEATURE_PR =
            new PullRequest(new PrNumber(42), new ForgeBranch("feature"), false, Checks.PENDING, Review.APPROVED);
    private static final PullRequestDetails DETAILS =
            StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
    private static final PullRequestPopup DIALOG = PullRequestPopup.loading(
                    new BranchName("mine"), FEATURE_PR, Instant.EPOCH, 0)
            .with(Either.right(DETAILS), 0);

    private static Model shown() {
        return shown(Checks.PENDING);
    }

    /**
     * The checks popup on {@code mine}, whose branch on GitHub shows its checks as {@code checks}.
     */
    private static Model shown(Checks checks) {
        return Update.update(model(checks), new ChecksMsg.Requested(GitHubChecksPort.PORT, MINE))
                .model();
    }

    @Test
    void theChecksOfALocalBranchAreThoseOfTheBranchItFollowsOnGitHub() {
        Next next = Update.update(model(Checks.PENDING), new ChecksMsg.Requested(GitHubChecksPort.PORT, MINE));

        assertEquals(
                Option.of(GitHubChecksFixtures.loading(MINE, FEATURE, Instant.EPOCH)),
                next.model().popup(ChecksPopup.class));
        assertTrue(next.cmds().contains(new LoadChecks(FEATURE)), next.cmds().toString());
    }

    @Test
    void aRemoteBranchShowsItsOwnChecks() {
        Next next = Update.update(
                model(Checks.FAILING), new ChecksMsg.Requested(GitHubChecksPort.PORT, new BranchRef.Remote(FEATURE)));

        assertEquals(
                Option.of(GitHubChecksFixtures.loading(new BranchRef.Remote(FEATURE), FEATURE, Instant.EPOCH)),
                next.model().popup(ChecksPopup.class));
        assertEquals(List.of(new LoadChecks(FEATURE)), next.cmds());
    }

    @Test
    void aBranchWithoutChecksShowsNothing() {
        assertEquals(
                Option.none(),
                Update.update(model(Checks.NONE), new ChecksMsg.Requested(GitHubChecksPort.PORT, MINE))
                        .model()
                        .popup(ChecksPopup.class));
        assertEquals(
                Option.none(),
                Update.update(
                                model(Checks.PENDING),
                                new ChecksMsg.Requested(
                                        GitHubChecksPort.PORT,
                                        new BranchRef.Remote(new RemoteBranch("fork", new BranchName("feature")))))
                        .model()
                        .popup(ChecksPopup.class));
    }

    @Test
    void whatWasReadIsShownAndLearnedFrom() {
        Next next = Update.update(shown(Checks.PASSING), LoadChecks.loaded(FEATURE, READ));

        assertEquals(
                Option.of(GitHubChecksFixtures.loading(MINE, FEATURE, Instant.EPOCH)
                        .showing(GitHubChecksFixtures.readOf(FEATURE, READ))),
                next.model().popup(ChecksPopup.class));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        new RepoSlug("owner/repo"),
                        List.of(CheckSample.of(PASSED).get()),
                        next.model().now(),
                        false)),
                next.cmds());
    }

    @Test
    void theBranchesAreReadAgainWhenTheChecksReadDoOtherwiseThanTheBranchShows() {
        Next next = Update.update(shown(), LoadChecks.loaded(FEATURE, READ));

        assertTrue(next.cmds().contains(new LoadBranchChecks()), next.cmds().toString());
        assertEquals(
                Option.of(Checks.PENDING), next.model().part(GitHubState.PART).checks(FEATURE));
    }

    @Test
    void theBranchesAreNotReadAgainWhenTheBranchShowsTheChecksAsRead() {
        Next passed = Update.update(shown(Checks.PASSING), LoadChecks.loaded(FEATURE, READ));
        Next running = Update.update(shown(), LoadChecks.loaded(FEATURE, RUNNING));

        assertFalse(
                passed.cmds().contains(new LoadBranchChecks()), passed.cmds().toString());
        assertFalse(
                running.cmds().contains(new LoadBranchChecks()), running.cmds().toString());
    }

    // The checks were read for the tip before a push; the branch read since says those of the new tip run.
    @Test
    void anEarlierReadOfTheChecksLeavesTheBranchAsALaterReadOfTheBranchesShowsIt() {
        Model pushed = Update.update(
                        shown(Checks.PASSING),
                        new BranchChecksMsg.Loaded(new GitHubChecks(
                                HashSet.of(new RemoteName("origin")),
                                HashMap.of(new BranchName("feature"), Checks.PENDING))))
                .model();

        Next next = Update.update(pushed, LoadChecks.loaded(FEATURE, READ));

        assertEquals(
                Option.of(Checks.PENDING), next.model().part(GitHubState.PART).checks(FEATURE));
    }

    // As gh reads them when the branch is gone on GitHub.
    @Test
    void noChecksReadLeaveTheBranchesAsTheyAre() {
        Next next = Update.update(
                shown(), LoadChecks.loaded(FEATURE, new PendingChecks(new RepoSlug("owner/repo"), HashMap.empty())));

        assertFalse(next.cmds().contains(new LoadBranchChecks()), next.cmds().toString());
        assertEquals(shown().part(GitHubState.PART), next.model().part(GitHubState.PART));
    }

    @Test
    void checksReadOfABranchNotOnGitHubLeaveTheBranchesAsTheyAre() {
        RemoteBranch fork = new RemoteBranch("fork", new BranchName("feature"));

        Next next = Update.update(shown(), LoadChecks.loaded(fork, READ));

        assertFalse(next.cmds().contains(new LoadBranchChecks()), next.cmds().toString());
        assertEquals(shown().part(GitHubState.PART), next.model().part(GitHubState.PART));
    }

    // What was read is as true once the popup is closed.
    @Test
    void checksReadAfterClosingStillHaveTheBranchesReadAgain() {
        Model closed = Update.update(shown(), new PopupMsg.Dismissed(ChecksPopup.class))
                .model();

        Next next = Update.update(closed, LoadChecks.loaded(FEATURE, READ));

        assertTrue(next.cmds().contains(new LoadBranchChecks()), next.cmds().toString());
    }

    @Test
    void whatWasReadOfAnotherBranchLeavesThePopupAsItIs() {
        RemoteBranch other = new RemoteBranch("origin", new BranchName("other"));

        assertEquals(
                shown(), Update.update(shown(), LoadChecks.loaded(other, READ)).model());
        assertEquals(
                shown(),
                Update.update(shown(), LoadChecks.notLoaded(other, refused("offline")))
                        .model());
    }

    @Test
    void whatIsReadAfterClosingIsLearnedFromWithoutShowingItAgain() {
        Model closed = Update.update(shown(Checks.PASSING), new PopupMsg.Dismissed(ChecksPopup.class))
                .model();

        Next next = Update.update(closed, LoadChecks.loaded(FEATURE, READ));

        assertEquals(Option.none(), next.model().popup(ChecksPopup.class));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        new RepoSlug("owner/repo"),
                        List.of(CheckSample.of(PASSED).get()),
                        next.model().now(),
                        false)),
                next.cmds());
    }

    @Test
    void theChecksKeepWhereTheyAreScrolledToWhenReadAgainAndStartAtTheTopWhenShownAgain() {
        Model scrolled = Update.update(
                        Update.update(shown(), LoadChecks.loaded(FEATURE, READ)).model(),
                        new PopupMsg.ListCursorChanged(ListCursor.START.at(3)))
                .model();

        Model readAgain = Update.update(
                        Update.update(scrolled, new ChecksMsg.RefreshChosen()).model(),
                        LoadChecks.loaded(FEATURE, READ))
                .model();
        assertEquals(ListCursor.START.at(3), readAgain.listCursor());

        Model shownAgain = Update.update(
                        Update.update(readAgain, new PopupMsg.Dismissed(ChecksPopup.class))
                                .model(),
                        new ChecksMsg.Requested(GitHubChecksPort.PORT, MINE))
                .model();
        assertEquals(ListCursor.START, shownAgain.listCursor());
    }

    @Test
    void theChecksAreReadAgainByHandOnceRead() {
        Model none = model(Checks.PENDING);
        assertEquals(Next.of(none), Update.update(none, new ChecksMsg.Tick(GitHubChecksPort.PORT)));

        assertEquals(
                List.empty(),
                Update.update(shown(), new ChecksMsg.RefreshChosen()).cmds());

        Model read = Update.update(shown(), LoadChecks.loaded(FEATURE, READ)).model();
        Next again = Update.update(read.withNow(Instant.EPOCH.plusSeconds(5)), new ChecksMsg.RefreshChosen());

        assertEquals(List.of(new LoadChecks(FEATURE)), again.cmds());
        ChecksPopup reading = again.model().popup(ChecksPopup.class).get();
        assertEquals(Reading.ASKED, reading.reading());
        assertEquals(Instant.EPOCH.plusSeconds(5), reading.readAt());
        assertEquals(
                List.empty(),
                Update.update(
                                again.model().withNow(Instant.EPOCH.plusSeconds(3600)),
                                new ChecksMsg.Tick(GitHubChecksPort.PORT))
                        .cmds());
    }

    @Test
    void whileACheckIsNotDoneEveryTickReadsThemAgainByItself() {
        assertEquals(
                List.empty(),
                Update.update(shown(), new ChecksMsg.Tick(GitHubChecksPort.PORT))
                        .cmds());

        Model read = Update.update(shown(), LoadChecks.loaded(FEATURE, RUNNING)).model();
        Next again =
                Update.update(read.withNow(Instant.EPOCH.plusSeconds(10)), new ChecksMsg.Tick(GitHubChecksPort.PORT));

        assertEquals(List.of(new LoadChecks(FEATURE)), again.cmds());
        ChecksPopup reading = again.model().popup(ChecksPopup.class).get();
        assertEquals(Reading.BY_ITSELF, reading.reading());
        assertEquals(Instant.EPOCH.plusSeconds(10), reading.readAt());
    }

    @Test
    void onceAllAreDoneATickReadsThemAgainAMinuteAfterTheLastRead() {
        Model read = Update.update(shown(), LoadChecks.loaded(FEATURE, READ)).model();

        assertEquals(
                List.empty(),
                Update.update(read.withNow(Instant.EPOCH.plusSeconds(54)), new ChecksMsg.Tick(GitHubChecksPort.PORT))
                        .cmds());
        // The tick a minute on can come a few ms short of it.
        assertEquals(
                List.of(new LoadChecks(FEATURE)),
                Update.update(
                                read.withNow(Instant.EPOCH.plusSeconds(60).minusMillis(3)),
                                new ChecksMsg.Tick(GitHubChecksPort.PORT))
                        .cmds());
    }

    @Test
    void afterAReadFailedTheyAreReadAgainEveryMinuteOnly() {
        Model read = Update.update(shown(), LoadChecks.loaded(FEATURE, RUNNING)).model();
        Model reading = Update.update(
                        read.withNow(Instant.EPOCH.plusSeconds(10)), new ChecksMsg.Tick(GitHubChecksPort.PORT))
                .model();
        Model failed = Update.update(reading, LoadChecks.notLoaded(FEATURE, refused("offline")))
                .model();

        assertEquals(
                List.empty(),
                Update.update(failed.withNow(Instant.EPOCH.plusSeconds(20)), new ChecksMsg.Tick(GitHubChecksPort.PORT))
                        .cmds());
        assertEquals(
                List.of(new LoadChecks(FEATURE)),
                Update.update(failed.withNow(Instant.EPOCH.plusSeconds(70)), new ChecksMsg.Tick(GitHubChecksPort.PORT))
                        .cmds());
    }

    @Test
    void refreshingWhileTheyAreReadByThemselvesShowsThatReadAsAsked() {
        Model read = Update.update(shown(), LoadChecks.loaded(FEATURE, RUNNING)).model();
        Model quiet = Update.update(
                        read.withNow(Instant.EPOCH.plusSeconds(10)), new ChecksMsg.Tick(GitHubChecksPort.PORT))
                .model();

        Next asked = Update.update(quiet, new ChecksMsg.RefreshChosen());

        assertEquals(List.empty(), asked.cmds());
        assertEquals(Reading.ASKED, asked.model().popup(ChecksPopup.class).get().reading());
    }

    @Test
    void failingToReadThemAgainKeepsTheChecksMarkedStale() {
        Model read = Update.update(shown(), LoadChecks.loaded(FEATURE, READ)).model();

        ChecksPopup failed = Update.update(read, LoadChecks.notLoaded(FEATURE, refused("offline")))
                .model()
                .popup(ChecksPopup.class)
                .get();

        assertEquals(Option.of(GitHubChecksFixtures.readOf(FEATURE, READ)), failed.checks());
        assertTrue(failed.refreshFailed().isDefined());
    }

    @Test
    void closingHidesThePopup() {
        assertEquals(
                Option.none(),
                Update.update(shown(), new PopupMsg.Dismissed(ChecksPopup.class))
                        .model()
                        .popup(ChecksPopup.class));
    }

    @Test
    void closingTheChecksOfABranchHidesThem() {
        assertEquals(
                Option.none(),
                Update.update(shown(), new ChecksMsg.Closed()).model().popup());
    }

    @Test
    void thePullRequestDialogShowsTheChecksOfItsBranchOnGitHubAndClosingThemGoesBackToIt() {
        Next next = Update.update(
                model(Checks.PENDING).openPopup(DIALOG),
                new ChecksMsg.PullRequestChecksRequested(GitHubChecksPort.PORT));

        assertEquals(
                Option.of(GitHubChecksFixtures.loading(new BranchRef.Remote(FEATURE), FEATURE, Instant.EPOCH, DIALOG)),
                next.model().popup(ChecksPopup.class));
        assertEquals(List.of(new LoadChecks(FEATURE)), next.cmds());
        assertEquals(
                Option.of(DIALOG),
                Update.update(next.model(), new ChecksMsg.Closed()).model().popup());
    }

    @Test
    void aPullRequestWithoutChecksOrNotReadYetShowsNone() {
        Model noChecks = model(Checks.NONE).openPopup(DIALOG);
        Model loading = model(Checks.PENDING)
                .openPopup(PullRequestPopup.loading(new BranchName("mine"), FEATURE_PR, Instant.EPOCH, 0));

        assertEquals(
                Next.of(noChecks),
                Update.update(noChecks, new ChecksMsg.PullRequestChecksRequested(GitHubChecksPort.PORT)));
        assertEquals(
                Next.of(loading),
                Update.update(loading, new ChecksMsg.PullRequestChecksRequested(GitHubChecksPort.PORT)));
    }

    @Test
    void aPullRequestFromAForkShowsNoChecksThoughABranchHereHasItsName() {
        Model fork = model(Checks.PENDING).openPopup(DIALOG.ofFork());

        assertEquals(Option.none(), DIALOG.ofFork().checksBranch(fork));
        assertEquals(
                Next.of(fork), Update.update(fork, new ChecksMsg.PullRequestChecksRequested(GitHubChecksPort.PORT)));
    }

    @Test
    void whatIsReadOfThePullRequestChangesTheDialogTheChecksGoBackTo() {
        PullRequestPopup loading = PullRequestPopup.loading(new BranchName("mine"), FEATURE_PR, Instant.EPOCH, 0);
        Model checks = model(Checks.PENDING)
                .openPopup(
                        GitHubChecksFixtures.loading(new BranchRef.Remote(FEATURE), FEATURE, Instant.EPOCH, loading));

        Model read = Update.update(checks, new PullRequestMsg.DetailsLoaded(new PrNumber(42), 0, DETAILS))
                .model();

        assertEquals(
                Option.of(DETAILS),
                read.popup(ChecksPopup.class)
                        .flatMap(ChecksPopup::from)
                        .map(PullRequestPopup.class::cast)
                        .flatMap(PullRequestPopup::readDetails));
    }
}
