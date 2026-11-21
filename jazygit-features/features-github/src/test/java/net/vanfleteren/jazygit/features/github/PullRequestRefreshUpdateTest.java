package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.lastRead;
import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.features.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Reading the details of a shown pull request again, when asked or by itself on a tick.
 */
class PullRequestRefreshUpdateTest {

    /**
     * {@code model} a minute after the pull request was read, when it is read again by itself also with its checks
     * done (ADR 34).
     */
    private static Model aMinuteOn(Model model) {
        return model.withNow(model.now().plus(CheckTimings.READ_WHEN_DONE));
    }

    @Test
    void theTickReadsTheDetailsAgainByItselfAMinuteAfterTheLastReadWhenTheirChecksAreDone() {
        Model shown = showingDetails();

        assertEquals(
                List.empty(),
                Update.update(shown.withNow(shown.now().plusSeconds(54)), new PullRequestMsg.Tick())
                        .cmds());
        Next again = Update.update(aMinuteOn(shown), new PullRequestMsg.Tick());
        assertEquals(List.of(new LoadPullRequestDetails(new PrNumber(42), lastRead(again.model()))), again.cmds());
        assertEquals(
                Reading.BY_ITSELF,
                again.model().popup(PullRequestPopup.class).get().reading());
        assertEquals(
                Reading.ASKED,
                Update.update(shown, new PullRequestMsg.RefreshChosen())
                        .model()
                        .popup(PullRequestPopup.class)
                        .get()
                        .reading());
    }

    @Test
    void refreshingWhileTheDetailsAreReadByThemselvesShowsThatReadAsAsked() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();

        Next asked = Update.update(quiet, new PullRequestMsg.RefreshChosen());

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                Reading.ASKED, asked.model().popup(PullRequestPopup.class).get().reading());
    }

    @Test
    void aDraftChangedWhileTheDetailsAreReadByThemselvesDoesNotTakeThatRead() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();

        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();
        assertEquals(new SetDraft(FEATURE, new PrNumber(42), true), cmd);

        // The quiet read started before the change, and comes back after it, with how it was before; the pull
        // requests read again after the change list it as a draft.
        Model drafted = Update.update(
                        Update.update(drafting.model(), cmd.run(env())).model(),
                        PullRequestFixtures.listed(List.of(new PullRequest(
                                new PrNumber(42), new GhBranch("feature"), true, Checks.PASSING, Review.APPROVED))))
                .model();
        Model dropped = Update.update(
                        drafted, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(drafted), before))
                .model();
        PullRequestPopup popup = dropped.popup(PullRequestPopup.class).get();
        assertEquals(Option.of(true), popup.draft());
        assertEquals(Reading.NOT, popup.reading());
        assertEquals(
                drafted.part(GitHubState.PART).pullRequests(),
                dropped.part(GitHubState.PART).pullRequests());
    }

    @Test
    void aReadThatComesBackBeforeTheDraftChangeIsDoneIsTakenAndTheChangeShowsOnceDone() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());

        // The quiet read comes back while the change still runs: how it was then, which is taken.
        Model read = Update.update(
                        drafting.model(),
                        new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(drafting.model()), before))
                .model();
        assertEquals(Reading.NOT, read.popup(PullRequestPopup.class).get().reading());
        assertEquals(Option.of(false), read.popup(PullRequestPopup.class).get().draft());

        Model drafted = Update.update(read, ((SetDraft) drafting.cmds().head()).run(env()))
                .model();
        assertEquals(
                Option.of(true), drafted.popup(PullRequestPopup.class).get().draft());
    }

    @Test
    void aDroppedReadThatFailsMarksNothingStaleAndLetsTicksReadAgain() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();

        Model failed = Update.update(
                        drafted,
                        new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(drafted), refused("gh: offline")))
                .model();

        PullRequestPopup popup = failed.popup(PullRequestPopup.class).get();
        assertEquals(Option.none(), popup.refreshFailed());
        assertEquals(Option.of(true), popup.draft());
        assertEquals(Reading.NOT, popup.reading());
        assertEquals(
                List.of(new LoadPullRequestDetails(
                        new PrNumber(42), failed.part(GitHubState.PART).nextRead())),
                Update.update(aMinuteOn(failed), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void refreshingWhileAReadThatIsNoLongerTakenRunsStartsAFreshOne() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();

        Next asked = Update.update(drafted, new PullRequestMsg.RefreshChosen());

        assertEquals(
                List.of(new LoadPullRequestDetails(
                        new PrNumber(42), drafted.part(GitHubState.PART).nextRead())),
                asked.cmds());
        PullRequestPopup popup = asked.model().popup(PullRequestPopup.class).get();
        assertEquals(Reading.ASKED, popup.reading());
        assertEquals(drafted.part(GitHubState.PART).nextRead(), popup.read());
    }

    @Test
    void whileACheckIsNotDoneATickReadsTheDetailsAgainTenSecondsOn() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        Model running = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42),
                                lastRead(shown),
                                withARunningCheck(StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get())))
                .model();

        assertEquals(
                List.of(new LoadPullRequestDetails(
                        new PrNumber(42), running.part(GitHubState.PART).nextRead())),
                Update.update(running.withNow(running.now().plusSeconds(10)), new PullRequestMsg.Tick())
                        .cmds());
    }

    @Test
    void afterAReadFailedTheDetailsAreReadAgainEveryMinuteOnly() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        Model running = Update.update(
                        shown,
                        new PullRequestMsg.DetailsLoaded(
                                new PrNumber(42),
                                lastRead(shown),
                                withARunningCheck(StubGitHub.of()
                                        .pullRequests()
                                        .details(new PrNumber(42))
                                        .get())))
                .model();
        Model reading = Update.update(running.withNow(running.now().plusSeconds(10)), new PullRequestMsg.Tick())
                .model();
        Model failed = Update.update(
                        reading,
                        new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(reading), refused("gh: offline")))
                .model();

        assertEquals(
                List.empty(),
                Update.update(failed.withNow(failed.now().plusSeconds(10)), new PullRequestMsg.Tick())
                        .cmds());
        assertEquals(
                List.of(new LoadPullRequestDetails(
                        new PrNumber(42), failed.part(GitHubState.PART).nextRead())),
                Update.update(aMinuteOn(failed), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void aReadNotTakenAfterTheDialogWasClosedLeavesTheBranchesAsTheyWere() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();
        Model closed = Update.update(drafted, new PopupMsg.Dismissed(PullRequestPopup.class))
                .model();
        // The pull requests read again after the change list it as a draft.
        Model listed = Update.update(
                        closed,
                        PullRequestFixtures.listed(List.of(new PullRequest(
                                new PrNumber(42), new GhBranch("feature"), true, Checks.PASSING, Review.APPROVED))))
                .model();

        Model dropped = Update.update(
                        listed, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(listed), before))
                .model();

        assertEquals(
                listed.part(GitHubState.PART).pullRequests(),
                dropped.part(GitHubState.PART).pullRequests());
    }

    @Test
    void aDialogOpenedAgainLeavesAnOlderReadThatComesBackFirstToTheBranches() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        long older = lastRead(quiet);
        PullRequestDetails details =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        Model reopened = Update.update(
                        Update.update(quiet, new PopupMsg.Dismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.Requested(FEATURE))
                .model();
        PullRequestPopup loading = reopened.popup(PullRequestPopup.class).get();

        // The older read is still taken, as nothing changed since, but the dialog waits for its own.
        Model failed = Update.update(
                        reopened, new PullRequestMsg.DetailsFailed(new PrNumber(42), older, refused("gh: offline")))
                .model();
        assertEquals(Option.of(loading), failed.popup(PullRequestPopup.class));

        Model read = Update.update(
                        reopened, new PullRequestMsg.DetailsLoaded(new PrNumber(42), older, details.withDraft(true)))
                .model();
        assertEquals(Option.of(loading), read.popup(PullRequestPopup.class));
        assertEquals(
                Option.of(new PullRequest(
                        new PrNumber(42),
                        new GhBranch("feature"),
                        Option.of(new GhBranch("main")),
                        true,
                        Checks.NONE,
                        Review.NONE,
                        Option.none(),
                        Option.none())),
                read.part(GitHubState.PART).pullRequest(PUSHED));
    }

    @Test
    void aDialogOpenedAgainTakesItsOwnReadWhenItComesBackBeforeOneThatIsNoLongerTaken() {
        Model quiet = Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                .model();
        long stale = lastRead(quiet);
        PullRequestDetails before =
                quiet.popup(PullRequestPopup.class).get().details().get().get();
        Next drafting = Update.update(quiet, new PullRequestMsg.DraftChosen());
        Model drafted = Update.update(
                        drafting.model(), ((SetDraft) drafting.cmds().head()).run(env()))
                .model();
        Model reopened = Update.update(
                        Update.update(drafted, new PopupMsg.Dismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.Requested(FEATURE))
                .model();

        // Its own read comes back first, then the one that started before the change.
        Model own = Update.update(
                        reopened,
                        new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(reopened), before.withDraft(true)))
                .model();
        Model late = Update.update(own, new PullRequestMsg.DetailsLoaded(new PrNumber(42), stale, before))
                .model();

        PullRequestPopup popup = late.popup(PullRequestPopup.class).get();
        assertEquals(Option.of(true), popup.draft());
        assertEquals(Reading.NOT, popup.reading());
        assertEquals(
                own.part(GitHubState.PART).pullRequests(),
                late.part(GitHubState.PART).pullRequests());
    }

    @Test
    void refreshingReadsTheDetailsAgainWhileTheyStayShown() {
        Model shown = showingDetails();
        PullRequestPopup before = shown.popup(PullRequestPopup.class).get();

        Next refreshing = Update.update(shown, new PullRequestMsg.RefreshChosen());

        PullRequestPopup popup =
                refreshing.model().popup(PullRequestPopup.class).get();
        assertEquals(before.details(), popup.details());
        assertTrue(popup.refreshing());
        assertEquals(
                List.of(new LoadPullRequestDetails(new PrNumber(42), lastRead(refreshing.model()))), refreshing.cmds());
        // Once it is being read again, it is not read a second time.
        assertEquals(
                List.empty(),
                Update.update(refreshing.model(), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void theTickReadsTheDetailsAgainOnlyOnceTheFirstReadIsDone() {
        Model reading =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();

        assertEquals(
                List.empty(), Update.update(reading, new PullRequestMsg.Tick()).cmds());
        assertEquals(
                List.of(new LoadPullRequestDetails(
                        new PrNumber(42),
                        showingDetails().part(GitHubState.PART).nextRead())),
                Update.update(aMinuteOn(showingDetails()), new PullRequestMsg.Tick())
                        .cmds());
        assertEquals(
                List.empty(), Update.update(model(), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void failingToReadTheDetailsAgainKeepsThemShownAsStale() {
        Model refreshing = Update.update(showingDetails(), new PullRequestMsg.RefreshChosen())
                .model();
        var failure = refused("gh: offline");

        PullRequestPopup failed = Update.update(
                        refreshing, new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(refreshing), failure))
                .model()
                .popup(PullRequestPopup.class)
                .get();

        assertTrue(failed.detailsRead());
        assertFalse(failed.refreshing());
        assertEquals(Option.of(failure), failed.refreshFailed());

        Model again = Update.update(
                        Update.update(
                                        refreshing,
                                        new PullRequestMsg.DetailsFailed(
                                                new PrNumber(42), lastRead(refreshing), failure))
                                .model(),
                        new PullRequestMsg.RefreshChosen())
                .model();
        PullRequestPopup recovered = Update.update(
                        again, new LoadPullRequestDetails(new PrNumber(42), lastRead(again)).run(env()))
                .model()
                .popup(PullRequestPopup.class)
                .get();
        assertEquals(Option.none(), recovered.refreshFailed());
        assertFalse(recovered.refreshing());
    }

    @Test
    void theTickDoesNotReadTheDetailsUnderThePopupOpenedFromIt() {
        Model comments = Update.update(
                        showingDetails(), new net.vanfleteren.jazygit.features.github.comments.CommentsMsg.Requested())
                .model();

        assertTrue(comments.popup(net.vanfleteren.jazygit.features.github.comments.CommentsPopup.class)
                .isDefined());
        assertEquals(
                List.empty(), Update.update(comments, new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void aDraftIsNotChangedWhileTheDetailsAreReadAgain() {
        // A read that started before the change could come back after it, and show it undone.
        Model refreshing = Update.update(showingDetails(), new PullRequestMsg.RefreshChosen())
                .model();

        assertEquals(
                List.empty(),
                Update.update(refreshing, new PullRequestMsg.DraftChosen()).cmds());

        Model read = Update.update(
                        refreshing, new LoadPullRequestDetails(new PrNumber(42), lastRead(refreshing)).run(env()))
                .model();
        assertEquals(
                List.of(new SetDraft(FEATURE, new PrNumber(42), true)),
                Update.update(read, new PullRequestMsg.DraftChosen()).cmds());
    }

    @Test
    void theDetailsAreNotReadAgainWhileTheBranchIsBusy() {
        // A read running while it becomes a draft could miss that, and show it undone.
        Next drafting = TestModels.stepped(showingDetails(), new PullRequestMsg.DraftChosen());
        SetDraft cmd = (SetDraft) drafting.cmds().head();

        assertEquals(
                List.empty(),
                Update.update(drafting.model(), new PullRequestMsg.RefreshChosen())
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(drafting.model(), new PullRequestMsg.Tick()).cmds());

        Model done = TestModels.done(drafting.model(), cmd, cmd.run(env())).model();
        assertEquals(
                List.of(new LoadPullRequestDetails(
                        new PrNumber(42), done.part(GitHubState.PART).nextRead())),
                Update.update(aMinuteOn(done), new PullRequestMsg.Tick()).cmds());
    }

    @Test
    void detailsReadAgainWhileTheCommentsAreOpenShowOnceTheDialogIsBack() {
        Model refreshing = Update.update(showingDetails(), new PullRequestMsg.RefreshChosen())
                .model();
        Model comments = Update.update(
                        refreshing, new net.vanfleteren.jazygit.features.github.comments.CommentsMsg.Requested())
                .model();
        PullRequestDetails details =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();

        Model read = Update.update(
                        comments,
                        new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(comments), details.withDraft(true)))
                .model();

        PullRequestPopup kept = read.popup(net.vanfleteren.jazygit.features.github.comments.CommentsPopup.class)
                .map(ShowsPullRequest::pullRequestPopup)
                .get();
        assertFalse(kept.refreshing());
        assertEquals(Option.of(true), kept.draft());
    }

    @Test
    void aFailedFirstReadIsTriedAgainAndASecondFailureReplacesIt() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        Model failed = Update.update(
                        shown,
                        new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(shown), refused("gh: offline")))
                .model();

        Next again = Update.update(aMinuteOn(failed), new PullRequestMsg.Tick());
        assertEquals(List.of(new LoadPullRequestDetails(new PrNumber(42), lastRead(again.model()))), again.cmds());

        var second = refused("gh: not logged in");
        PullRequestPopup popup = Update.update(
                        again.model(),
                        new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(again.model()), second))
                .model()
                .popup(PullRequestPopup.class)
                .get();
        assertEquals(Option.of(Either.left(second)), popup.details());
        assertEquals(Option.none(), popup.refreshFailed());
        assertFalse(popup.refreshing());
    }
}
