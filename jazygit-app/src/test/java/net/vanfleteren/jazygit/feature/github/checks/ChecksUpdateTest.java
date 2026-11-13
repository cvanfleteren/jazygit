package net.vanfleteren.jazygit.feature.github.checks;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.BranchChecksMsg;
import net.vanfleteren.jazygit.state.github.CheckTimesCmd;
import net.vanfleteren.jazygit.state.github.GitHubChecks;
import net.vanfleteren.jazygit.state.github.PendingChecks;
import net.vanfleteren.jazygit.state.github.Reading;
import net.vanfleteren.jazygit.state.popup.ListCursor;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
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
            new PendingChecks("owner/repo", HashMap.of(new BranchName("feature"), List.of(PASSED)));
    private static final PendingChecks RUNNING = new PendingChecks(
            "owner/repo",
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
            new PullRequest(new PrNumber(42), new GhBranch("feature"), false, Checks.PENDING, Review.APPROVED);
    private static final PullRequestDetails DETAILS =
            StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
    private static final PullRequestPopup DIALOG = PullRequestPopup.loading(
                    new BranchName("mine"), FEATURE_PR, Instant.EPOCH, 0)
            .with(Either.right(DETAILS), 0);

    private static Model shown() {
        return Update.update(model(Checks.PENDING), new ChecksMsg.Requested(MINE))
                .model();
    }

    @Test
    void theChecksOfALocalBranchAreThoseOfTheBranchItFollowsOnGitHub() {
        Next next = Update.update(model(Checks.PENDING), new ChecksMsg.Requested(MINE));

        assertEquals(
                Option.of(ChecksPopup.loading(MINE, FEATURE, Instant.EPOCH)),
                next.model().popup(ChecksPopup.class));
        assertTrue(next.cmds().contains(new LoadChecks(FEATURE)), next.cmds().toString());
    }

    @Test
    void aRemoteBranchShowsItsOwnChecks() {
        Next next = Update.update(model(Checks.FAILING), new ChecksMsg.Requested(new BranchRef.Remote(FEATURE)));

        assertEquals(
                Option.of(ChecksPopup.loading(new BranchRef.Remote(FEATURE), FEATURE, Instant.EPOCH)),
                next.model().popup(ChecksPopup.class));
        assertEquals(List.of(new LoadChecks(FEATURE)), next.cmds());
    }

    @Test
    void aBranchWithoutChecksShowsNothing() {
        assertEquals(
                Option.none(),
                Update.update(model(Checks.NONE), new ChecksMsg.Requested(MINE))
                        .model()
                        .popup(ChecksPopup.class));
        assertEquals(
                Option.none(),
                Update.update(
                                model(Checks.PENDING),
                                new ChecksMsg.Requested(
                                        new BranchRef.Remote(new RemoteBranch("fork", new BranchName("feature")))))
                        .model()
                        .popup(ChecksPopup.class));
    }

    @Test
    void whatWasReadIsShownAndLearnedFrom() {
        Next next = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ));

        assertEquals(
                Option.of(ChecksPopup.loading(MINE, FEATURE, Instant.EPOCH).with(Either.right(READ))),
                next.model().popup(ChecksPopup.class));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        "owner/repo",
                        List.of(CheckSample.of(PASSED).get()),
                        next.model().now(),
                        false)),
                next.cmds());
    }

    @Test
    void whatWasReadOfAnotherBranchLeavesThePopupAsItIs() {
        RemoteBranch other = new RemoteBranch("origin", new BranchName("other"));

        assertEquals(
                shown(),
                Update.update(shown(), new ChecksMsg.Loaded(other, READ)).model());
        assertEquals(
                shown(),
                Update.update(shown(), new ChecksMsg.Failed(other, refused("offline")))
                        .model());
    }

    @Test
    void whatIsReadAfterClosingIsLearnedFromWithoutShowingItAgain() {
        Model closed = Update.update(shown(), new PopupMsg.Dismissed(ChecksPopup.class))
                .model();

        Next next = Update.update(closed, new ChecksMsg.Loaded(FEATURE, READ));

        assertEquals(Option.none(), next.model().popup(ChecksPopup.class));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        "owner/repo",
                        List.of(CheckSample.of(PASSED).get()),
                        next.model().now(),
                        false)),
                next.cmds());
    }

    @Test
    void theChecksKeepWhereTheyAreScrolledToWhenReadAgainAndStartAtTheTopWhenShownAgain() {
        Model scrolled = Update.update(
                        Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ))
                                .model(),
                        new PopupMsg.ListCursorChanged(ListCursor.START.at(3)))
                .model();

        Model readAgain = Update.update(
                        Update.update(scrolled, new ChecksMsg.RefreshChosen()).model(),
                        new ChecksMsg.Loaded(FEATURE, READ))
                .model();
        assertEquals(ListCursor.START.at(3), readAgain.listCursor());

        Model shownAgain = Update.update(
                        Update.update(readAgain, new PopupMsg.Dismissed(ChecksPopup.class))
                                .model(),
                        new ChecksMsg.Requested(MINE))
                .model();
        assertEquals(ListCursor.START, shownAgain.listCursor());
    }

    @Test
    void theChecksAreReadAgainByHandOnceRead() {
        Model none = model(Checks.PENDING);
        assertEquals(Next.of(none), Update.update(none, new ChecksMsg.Tick()));

        assertEquals(
                List.empty(),
                Update.update(shown(), new ChecksMsg.RefreshChosen()).cmds());

        Model read = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ)).model();
        Next again = Update.update(read.withNow(Instant.EPOCH.plusSeconds(5)), new ChecksMsg.RefreshChosen());

        assertEquals(List.of(new LoadChecks(FEATURE)), again.cmds());
        ChecksPopup reading = again.model().popup(ChecksPopup.class).get();
        assertEquals(Reading.ASKED, reading.reading());
        assertEquals(Instant.EPOCH.plusSeconds(5), reading.readAt());
        assertEquals(
                List.empty(),
                Update.update(again.model().withNow(Instant.EPOCH.plusSeconds(3600)), new ChecksMsg.Tick())
                        .cmds());
    }

    @Test
    void whileACheckIsNotDoneEveryTickReadsThemAgainByItself() {
        assertEquals(List.empty(), Update.update(shown(), new ChecksMsg.Tick()).cmds());

        Model read =
                Update.update(shown(), new ChecksMsg.Loaded(FEATURE, RUNNING)).model();
        Next again = Update.update(read.withNow(Instant.EPOCH.plusSeconds(10)), new ChecksMsg.Tick());

        assertEquals(List.of(new LoadChecks(FEATURE)), again.cmds());
        ChecksPopup reading = again.model().popup(ChecksPopup.class).get();
        assertEquals(Reading.BY_ITSELF, reading.reading());
        assertEquals(Instant.EPOCH.plusSeconds(10), reading.readAt());
    }

    @Test
    void onceAllAreDoneATickReadsThemAgainAMinuteAfterTheLastRead() {
        Model read = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ)).model();

        assertEquals(
                List.empty(),
                Update.update(read.withNow(Instant.EPOCH.plusSeconds(54)), new ChecksMsg.Tick())
                        .cmds());
        // The tick a minute on can come a few ms short of it.
        assertEquals(
                List.of(new LoadChecks(FEATURE)),
                Update.update(read.withNow(Instant.EPOCH.plusSeconds(60).minusMillis(3)), new ChecksMsg.Tick())
                        .cmds());
    }

    @Test
    void afterAReadFailedTheyAreReadAgainEveryMinuteOnly() {
        Model read =
                Update.update(shown(), new ChecksMsg.Loaded(FEATURE, RUNNING)).model();
        Model reading = Update.update(read.withNow(Instant.EPOCH.plusSeconds(10)), new ChecksMsg.Tick())
                .model();
        Model failed = Update.update(reading, new ChecksMsg.Failed(FEATURE, refused("offline")))
                .model();

        assertEquals(
                List.empty(),
                Update.update(failed.withNow(Instant.EPOCH.plusSeconds(20)), new ChecksMsg.Tick())
                        .cmds());
        assertEquals(
                List.of(new LoadChecks(FEATURE)),
                Update.update(failed.withNow(Instant.EPOCH.plusSeconds(70)), new ChecksMsg.Tick())
                        .cmds());
    }

    @Test
    void refreshingWhileTheyAreReadByThemselvesShowsThatReadAsAsked() {
        Model read =
                Update.update(shown(), new ChecksMsg.Loaded(FEATURE, RUNNING)).model();
        Model quiet = Update.update(read.withNow(Instant.EPOCH.plusSeconds(10)), new ChecksMsg.Tick())
                .model();

        Next asked = Update.update(quiet, new ChecksMsg.RefreshChosen());

        assertEquals(List.empty(), asked.cmds());
        assertEquals(Reading.ASKED, asked.model().popup(ChecksPopup.class).get().reading());
    }

    @Test
    void failingToReadThemAgainKeepsTheChecksMarkedStale() {
        Model read = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ)).model();

        ChecksPopup failed = Update.update(read, new ChecksMsg.Failed(FEATURE, refused("offline")))
                .model()
                .popup(ChecksPopup.class)
                .get();

        assertEquals(Option.of(Either.right(READ)), failed.checks());
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
        Next next = Update.update(model(Checks.PENDING).openPopup(DIALOG), new ChecksMsg.PullRequestChecksRequested());

        assertEquals(
                Option.of(ChecksPopup.loading(new BranchRef.Remote(FEATURE), FEATURE, Instant.EPOCH, DIALOG)),
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

        assertEquals(Next.of(noChecks), Update.update(noChecks, new ChecksMsg.PullRequestChecksRequested()));
        assertEquals(Next.of(loading), Update.update(loading, new ChecksMsg.PullRequestChecksRequested()));
    }

    @Test
    void aPullRequestFromAForkShowsNoChecksThoughABranchHereHasItsName() {
        Model fork = model(Checks.PENDING).openPopup(DIALOG.ofFork());

        assertEquals(Option.none(), ChecksUpdate.ofPullRequest(fork, DIALOG.ofFork()));
        assertEquals(Next.of(fork), Update.update(fork, new ChecksMsg.PullRequestChecksRequested()));
    }

    @Test
    void whatIsReadOfThePullRequestChangesTheDialogTheChecksGoBackTo() {
        PullRequestPopup loading = PullRequestPopup.loading(new BranchName("mine"), FEATURE_PR, Instant.EPOCH, 0);
        Model checks = model(Checks.PENDING)
                .openPopup(ChecksPopup.loading(new BranchRef.Remote(FEATURE), FEATURE, Instant.EPOCH, loading));

        Model read = Update.update(checks, new PullRequestMsg.DetailsLoaded(new PrNumber(42), 0, DETAILS))
                .model();

        assertEquals(
                Option.of(DETAILS),
                read.popup(ChecksPopup.class).flatMap(ChecksPopup::from).flatMap(PullRequestPopup::readDetails));
    }
}
