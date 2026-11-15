package net.vanfleteren.jazygit.feature.github;

import static net.vanfleteren.jazygit.feature.github.PullRequestFixtures.*;
import static net.vanfleteren.jazygit.state.TestModels.*;
import static net.vanfleteren.jazygit.state.TestModels.lastRead;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestCmd.*;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.operation.*;
import net.vanfleteren.jazygit.state.popup.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * Showing the pull request of a branch, or one picked on the pull requests screen, and reading its details.
 */
class PullRequestDetailsUpdateTest {

    @Test
    void aBranchWithAPullRequestShowsItWhileItsDetailsAreRead() {
        Next shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")));

        assertEquals(
                Option.of(PullRequestPopup.loading(new BranchName("feature"), FEATURE_PR, Instant.EPOCH, 0)),
                shown.model().popup());
        assertEquals(List.of(new LoadPullRequestDetails(new PrNumber(42), lastRead(shown.model()))), shown.cmds());
    }

    @Test
    void theDetailsReadShowInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        PullRequestDetails details =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();

        Model loaded = Update.update(shown, new LoadPullRequestDetails(new PrNumber(42), lastRead(shown)).run(env()))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new BranchName("feature"), FEATURE_PR, Instant.EPOCH, 0)
                        .with(Either.right(details), 0)),
                loaded.popup());
    }

    @Test
    void theDetailsReadShowOnTheBranchToo() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        PullRequestDetails details =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        PullRequestDetails failing = new PullRequestDetails(
                new PrNumber(42),
                details.title(),
                details.author(),
                details.status().withDraft(true),
                details.branches(),
                details.activity(),
                details.size(),
                new Conversation(
                        Review.CHANGES_REQUESTED,
                        List.empty(),
                        0,
                        details.conversation().reviewThreads(),
                        List.empty()),
                List.of(
                        new PullRequestDetails.Check("build", Checks.PASSING),
                        new PullRequestDetails.Check("test", Checks.FAILING)));

        Model loaded = Update.update(
                        shown, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(shown), failing))
                .model();
        // Also when the dialog was closed meanwhile.
        Model closed = Update.update(
                        Update.update(shown, new PopupMsg.Dismissed(PullRequestPopup.class))
                                .model(),
                        new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(shown), failing))
                .model();

        PullRequest now = new PullRequest(
                new PrNumber(42),
                new GhBranch("feature"),
                Option.of(new GhBranch("main")),
                true,
                Checks.FAILING,
                Review.CHANGES_REQUESTED,
                Option.none(),
                Option.none());
        assertEquals(Option.of(now), loaded.gitHub().pullRequest(PUSHED));
        assertEquals(Option.of(now), closed.gitHub().pullRequest(PUSHED));
    }

    @Test
    void aPullRequestTheDetailsShowMergedIsNoLongerOnTheBranch() {
        Model shown =
                Update.update(withPr(), new PullRequestMsg.Requested(FEATURE)).model();
        PullRequestDetails details =
                StubGitHub.of().pullRequests().details(new PrNumber(42)).get();
        PullRequestDetails merged = details.withStatus(new Status(
                PullRequestDetails.State.MERGED,
                details.status().draft(),
                details.status().mergeable()));

        Model loaded = Update.update(shown, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(shown), merged))
                .model();

        assertEquals(Option.none(), loaded.gitHub().pullRequest(PUSHED));
    }

    @Test
    void failingToReadTheDetailsShowsWhyInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        var failure = refused("gh: not logged in");

        Model failed = Update.update(
                        shown, new PullRequestMsg.DetailsFailed(new PrNumber(42), lastRead(shown), failure))
                .model();

        assertEquals(
                Option.of(PullRequestPopup.loading(new BranchName("feature"), FEATURE_PR, Instant.EPOCH, 0)
                        .with(Either.left(failure), 0)),
                failed.popup());
        assertEquals(Option.none(), failed.outcome().error());
    }

    @Test
    void detailsReadAfterThePopupClosedOrForAnotherPullRequestAreDropped() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();
        PullRequestDetails details =
                StubGitHub.of().pullRequests().details(new PrNumber(7)).get();

        Model other = Update.update(shown, new PullRequestMsg.DetailsLoaded(new PrNumber(7), lastRead(shown), details))
                .model();
        assertEquals(shown.popup(), other.popup());
        assertEquals(shown.gitHub().pullRequests(), other.gitHub().pullRequests());
        Model closed = Update.update(shown, new PopupMsg.Dismissed(PullRequestPopup.class))
                .model();
        assertEquals(Option.none(), closed.popup());
        Model late = Update.update(
                        closed, new PullRequestMsg.DetailsLoaded(new PrNumber(42), lastRead(closed), details))
                .model();
        assertEquals(Option.none(), late.popup());
        assertEquals(closed.gitHub().pullRequests(), late.gitHub().pullRequests());
    }

    @Test
    void openingThePullRequestInTheBrowserKeepsTheDialogOpen() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested(new BranchName("feature")))
                .model();

        Next opened = Update.update(shown, new PullRequestMsg.OpenConfirmed());

        assertEquals(List.of(new OpenPullRequest(new BranchName("feature"), new PrNumber(42))), opened.cmds());
        assertEquals(shown.popup(), opened.model().popup());

        Next done = Update.update(
                opened.model(),
                new OpenPullRequest(new BranchName("feature"), new PrNumber(42)).succeeded(List.empty()));
        assertEquals(shown.popup(), done.model().popup());
    }

    @Test
    void thePullRequestOfABranchIsFoundByItsNameOnTheRemote() {
        Branch renamed = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.gitHub().pullRequest(renamed));
        assertEquals(Option.none(), withPr.gitHub().pullRequest(UNPUSHED));
    }

    @Test
    void thePullRequestOfABranchIsFoundEvenWhenItsRemoteNameHasASlash() {
        Branch onFork = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("6"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(
                        new BranchRef.Remote(new RemoteBranch("my/fork", new BranchName("feature"))), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), withPr.gitHub().pullRequest(onFork));
    }

    @Test
    void askingForAPullRequestAfterFailingToReadThemTellsWhy() {
        var failure = refused("gh: not logged in");
        Model failed =
                Update.update(model(), new PullRequestsMsg.LoadFailed(failure)).model();

        Next asked = Update.update(failed, new PullRequestMsg.Requested(new BranchName("feature")));

        assertEquals(
                Option.of(new PullRequestsError.Unavailable(failure)),
                asked.model().outcome().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void aPullRequestFromThePullRequestsScreenIsShownAndItsDetailsRead() {
        PullRequestSummary summary = new PullRequestSummary(
                "owner/repo",
                new PrNumber(9),
                "From a fork",
                "bob",
                new GhBranch("patch-1"),
                true,
                true,
                Checks.FAILING,
                Review.REQUIRED,
                Instant.EPOCH,
                "https://github.com/owner/repo/pull/9");

        Model ofOwnerRepo = loaded().withPrOverview(
                        net.vanfleteren.jazygit.state.github.prs.PrOverview.INITIAL.withRepositoryName(
                                Option.of("owner/repo")));

        Next shown = Update.update(ofOwnerRepo, new PullRequestMsg.SummaryRequested(summary));

        // From a fork, so its head branch is not the branch of that name here.
        assertEquals(
                Option.of(PullRequestPopup.loading(
                                new BranchName("patch-1"),
                                new PullRequest(
                                        new PrNumber(9),
                                        new GhBranch("patch-1"),
                                        true,
                                        Checks.FAILING,
                                        Review.REQUIRED),
                                Instant.EPOCH,
                                0)
                        .ofFork()),
                shown.model().popup(PullRequestPopup.class));
        assertEquals(List.of(new LoadPullRequestDetails(new PrNumber(9), lastRead(shown.model()))), shown.cmds());
    }

    @Test
    void aPullRequestOfAnotherRepositoryOpensInTheBrowser() {
        PullRequestSummary elsewhere = new PullRequestSummary(
                "other/repo",
                new PrNumber(3),
                "Elsewhere",
                "carol",
                new GhBranch("fix"),
                false,
                false,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/other/repo/pull/3");
        Model ofOwnerRepo = loaded().withPrOverview(
                        net.vanfleteren.jazygit.state.github.prs.PrOverview.INITIAL.withRepositoryName(
                                Option.of("owner/repo")));

        Next opened = Update.update(ofOwnerRepo, new PullRequestMsg.SummaryRequested(elsewhere));

        assertEquals(Option.none(), opened.model().popup());
        assertEquals(List.of(new BrowsePullRequestCmd(elsewhere)), opened.cmds());
    }
}
