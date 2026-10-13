package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.LoadPullRequestDetails;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.LoadPullRequests;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.OpenPullRequest;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullRequestUpdateTest {

    private static final Branch MAIN = new Branch("main", true, new Sha("1"), Instant.EPOCH, true, true,
            Option.of(new Upstream(new RemoteBranch("origin", "main"), 0, 0)));
    private static final Branch RELEASE = new Branch("release", false, new Sha("2"), Instant.EPOCH, true, false,
            Option.of(new Upstream(new RemoteBranch("origin", "release"), 0, 0)));
    private static final Branch PUSHED = new Branch("feature", false, new Sha("3"), Instant.EPOCH, true, false,
            Option.of(new Upstream(new RemoteBranch("origin", "feature"), 0, 0)));
    private static final Branch UNPUSHED = new Branch("local", false, new Sha("4"));
    private static final Branch AHEAD = new Branch("ahead", false, new Sha("5"), Instant.EPOCH, true, false,
            Option.of(new Upstream(new RemoteBranch("origin", "ahead"), 2, 0)));
    private static final PullRequest FEATURE_PR = new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);

    private static Model withPr() {
        return Update.update(model(), new PullRequestMsg.Loaded(List.of(FEATURE_PR))).model();
    }

    private static Env env() {
        return new Env(new StubGitProvider() {
        }, StubGitHub.empty());
    }

    private static Model model() {
        // The default branch is listed last, to see it offered first.
        return Update.update(loaded(), new LoadMsg.BranchesLoaded(List.of(RELEASE, PUSHED, UNPUSHED, AHEAD, MAIN)))
                .model();
    }

    @Test
    void aPushedBranchWithoutPullRequestAsksForTheBaseWithTheDefaultBranchFirst() {
        Next asked = Update.update(model(), new PullRequestMsg.Requested("feature"));

        assertEquals(Option.of(new BasePopup("feature", List.of("main", "release", "ahead"))), asked.model().popup());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void choosingTheBaseCreatesThePullRequestAndKeepsTheBranchBusy() {
        Model asked = Update.update(model(), new PullRequestMsg.Requested("feature")).model();

        Next created = Update.update(asked, new PullRequestMsg.BaseChosen("main"));

        assertEquals(List.of(new CreatePullRequest("feature", "feature", "main")), created.cmds());
        assertEquals(Option.none(), created.model().popup());
        assertEquals(Option.of(Operation.CREATE_PR), created.model().busy().get("feature"));
    }

    @Test
    void cancellingTheBaseClosesThePopup() {
        Model asked = Update.update(model(), new PullRequestMsg.Requested("feature")).model();

        assertEquals(Option.none(), Update.update(asked, new PullRequestMsg.BaseCancelled()).model().popup());
    }

    @Test
    void aBranchThatIsNotPushedOrHasUnpushedCommitsMustBePushedFirst() {
        assertEquals(Option.of(new AppError.NotPushed("local")),
                Update.update(model(), new PullRequestMsg.Requested("local")).model().error());
        assertEquals(Option.of(new AppError.NotPushed("ahead")),
                Update.update(model(), new PullRequestMsg.Requested("ahead")).model().error());
    }

    @Test
    void aBranchWithAPullRequestShowsItWhileItsDetailsAreRead() {
        Next shown = Update.update(withPr(), new PullRequestMsg.Requested("feature"));

        assertEquals(Option.of(PullRequestPopup.loading("feature", FEATURE_PR)), shown.model().popup());
        assertEquals(List.of(new LoadPullRequestDetails(42)), shown.cmds());
    }

    @Test
    void theDetailsReadShowInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested("feature")).model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(42).get();

        Model loaded = Update.update(shown, new LoadPullRequestDetails(42).run(env())).model();

        assertEquals(Option.of(PullRequestPopup.loading("feature", FEATURE_PR).with(Either.right(details))),
                loaded.popup());
    }

    @Test
    void failingToReadTheDetailsShowsWhyInThePopup() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested("feature")).model();
        var failure = refused("gh: not logged in");

        Model failed = Update.update(shown, new PullRequestMsg.DetailsFailed(42, failure)).model();

        assertEquals(Option.of(PullRequestPopup.loading("feature", FEATURE_PR).with(Either.left(failure))),
                failed.popup());
        assertEquals(Option.none(), failed.error());
    }

    @Test
    void detailsReadAfterThePopupClosedOrForAnotherPullRequestAreDropped() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested("feature")).model();
        PullRequestDetails details = StubGitHub.empty().pullRequestDetails(7).get();

        assertEquals(shown, Update.update(shown, new PullRequestMsg.DetailsLoaded(7, details)).model());
        Model closed = Update.update(shown, new PullRequestMsg.InfoClosed()).model();
        assertEquals(Option.none(), closed.popup());
        assertEquals(closed, Update.update(closed, new PullRequestMsg.DetailsLoaded(42, details)).model());
    }

    @Test
    void confirmingThePopupOpensThePullRequestInTheBrowser() {
        Model shown = Update.update(withPr(), new PullRequestMsg.Requested("feature")).model();

        Next opened = Update.update(shown, new PullRequestMsg.OpenConfirmed());

        assertEquals(List.of(new OpenPullRequest("feature", 42)), opened.cmds());
        assertEquals(Option.none(), opened.model().popup());
    }

    @Test
    void thePullRequestOfABranchIsFoundByItsNameOnTheRemote() {
        Branch renamed = new Branch("mine", false, new Sha("6"), Instant.EPOCH, true, false,
                Option.of(new Upstream(new RemoteBranch("origin", "feature"), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestMsg.Loaded(List.of(FEATURE_PR))).model();

        assertEquals(Option.of(FEATURE_PR), withPr.pullRequest(renamed));
        assertEquals(Option.none(), withPr.pullRequest(UNPUSHED));
    }

    @Test
    void thePullRequestOfABranchIsFoundEvenWhenItsRemoteNameHasASlash() {
        Branch onFork = new Branch("mine", false, new Sha("6"), Instant.EPOCH, true, false,
                Option.of(new Upstream(new RemoteBranch("my/fork", "feature"), 0, 0)));
        Model withPr = Update.update(model(), new PullRequestMsg.Loaded(List.of(FEATURE_PR))).model();

        assertEquals(Option.of(FEATURE_PR), withPr.pullRequest(onFork));
    }

    @Test
    void ofSeveralPullRequestsFromTheSameBranchTheNewestIsKeptWhateverTheOrder() {
        PullRequest older = new PullRequest(7, "feature", false, Checks.NONE, Review.NONE);

        assertEquals(Option.of(FEATURE_PR),
                Update.update(model(), new PullRequestMsg.Loaded(List.of(older, FEATURE_PR))).model().pullRequests().get("feature"));
        assertEquals(Option.of(FEATURE_PR),
                Update.update(model(), new PullRequestMsg.Loaded(List.of(FEATURE_PR, older))).model().pullRequests().get("feature"));
    }

    @Test
    void failingToReadThePullRequestsKeepsTheOnesShownWithoutAnError() {
        Model withPr = Update.update(model(), new PullRequestMsg.Loaded(List.of(FEATURE_PR))).model();

        Model failed = Update.update(withPr, new PullRequestMsg.LoadFailed(refused("gh: not logged in"))).model();

        assertEquals(withPr.pullRequests(), failed.pullRequests());
        assertEquals(Option.none(), failed.error());
        assertEquals(Option.none(), failed.popup());
    }

    @Test
    void askingForAPullRequestAfterFailingToReadThemTellsWhy() {
        var failure = refused("gh: not logged in");
        Model failed = Update.update(model(), new PullRequestMsg.LoadFailed(failure)).model();

        Next asked = Update.update(failed, new PullRequestMsg.Requested("feature"));

        assertEquals(Option.of(new AppError.PullRequestsUnavailable(failure)), asked.model().error());
        assertEquals(List.empty(), asked.cmds());
    }

    @Test
    void readingThePullRequestsAgainForgetsTheFailure() {
        Model failed = Update.update(model(), new PullRequestMsg.LoadFailed(refused("offline"))).model();

        Model loaded = Update.update(failed, new PullRequestMsg.Loaded(List.empty())).model();

        assertEquals(Option.none(), loaded.pullRequestsFailure());
    }

    @Test
    void aCreatedPullRequestIsLoggedAndThePullRequestsAreReadAgain() {
        Model asked = Update.update(model(), new PullRequestMsg.Requested("feature")).model();
        Model creating = Update.update(asked, new PullRequestMsg.BaseChosen("main")).model();
        CreatePullRequest cmd = new CreatePullRequest("feature", "feature", "main");

        Next done = Update.update(creating, cmd.run(new Env(new StubGitProvider() {
        }, StubGitHub.empty())));

        assertTrue(done.cmds().contains(new LoadPullRequests()), done.cmds().toString());
        assertEquals(Option.none(), done.model().busy().get("feature"));
        assertEquals("gh pr create --head feature --base main --fill",
                done.model().commandLog().last().commands().last().line());
    }

    @Test
    void aCreatedPullRequestShowsAtOnceEvenIfGitHubDoesNotListItYet() {
        CreatePullRequest cmd = new CreatePullRequest("feature", "feature", "main");
        PullRequest created = new PullRequest(43, "feature", false, Checks.NONE, Review.NONE);
        Model creating = Update.update(Update.update(model(), new PullRequestMsg.Requested("feature")).model(),
                new PullRequestMsg.BaseChosen("main")).model();

        Next done = Update.update(creating, new PullRequestMsg.Created(cmd, Option.of(created), List.empty()));

        assertEquals(Option.of(created), done.model().pullRequests().get("feature"));
        assertTrue(done.cmds().contains(new LoadPullRequests()));
    }

    @Test
    void aReadOfThePullRequestsIsCountedWhileItRunsAndNotStartedTwice() {
        Next reading = Update.update(model(), new PullRequestMsg.Refresh());
        assertEquals(1, reading.model().readingPullRequests());

        Next again = Update.update(reading.model(), new PullRequestMsg.Refresh());
        assertEquals(List.empty(), again.cmds());
        assertEquals(1, again.model().readingPullRequests());

        assertEquals(0, Update.update(reading.model(), new PullRequestMsg.Loaded(List.empty())).model().readingPullRequests());
        assertEquals(0, Update.update(reading.model(), new PullRequestMsg.LoadFailed(refused("offline"))).model()
                .readingPullRequests());
    }

    @Test
    void refreshingReadsThePullRequests() {
        assertEquals(List.of(new LoadPullRequests()), Update.update(model(), new PullRequestMsg.Refresh()).cmds());
        assertInstanceOf(PullRequestMsg.Loaded.class,
                new LoadPullRequests().run(new Env(new StubGitProvider() {
                }, StubGitHub.empty())));
    }
}
