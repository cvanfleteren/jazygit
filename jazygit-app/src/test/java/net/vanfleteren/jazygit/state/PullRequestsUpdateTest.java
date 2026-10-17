package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Reading the open pull requests from GitHub.
 */
class PullRequestsUpdateTest {

    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);

    @Test
    void ofSeveralPullRequestsFromTheSameBranchTheNewestIsKeptWhateverTheOrder() {
        PullRequest older = new PullRequest(7, "feature", false, Checks.NONE, Review.NONE);

        assertEquals(
                Option.of(FEATURE_PR),
                Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(older, FEATURE_PR)))
                        .model()
                        .gitHub()
                        .pullRequests()
                        .flatMap(open -> open.get(new Branch.Name("feature"))));
        assertEquals(
                Option.of(FEATURE_PR),
                Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR, older)))
                        .model()
                        .gitHub()
                        .pullRequests()
                        .flatMap(open -> open.get(new Branch.Name("feature"))));
    }

    @Test
    void failingToReadThePullRequestsKeepsTheOnesShownWithoutAnError() {
        Model withPr = Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        Model failed = Update.update(withPr, new PullRequestsMsg.LoadFailed(refused("gh: not logged in")))
                .model();

        assertEquals(withPr.gitHub().pullRequests(), failed.gitHub().pullRequests());
        assertEquals(Option.none(), failed.outcome().error());
        assertEquals(Option.none(), failed.popup());
    }

    @Test
    void theAccountChosenForTheRepositoryNotBeingAvailableIsShownOnce() {
        Failure unavailable = new Failure.GitHub(
                new GhError.TokenUnavailable(
                        new GhAccount("github.com", "alice-work"),
                        new GhError.CommandFailed(Option.of("no oauth token found"), 1)),
                List.empty());

        Model failed = Update.update(loaded(), new PullRequestsMsg.LoadFailed(unavailable))
                .model();
        Model again = Update.update(
                        failed.clearError().withoutPopup(ErrorPopup.class), new PullRequestsMsg.LoadFailed(unavailable))
                .model();

        assertEquals(
                Option.of(new AppError.PullRequestsUnavailable(unavailable)),
                failed.outcome().error());
        assertEquals(Option.none(), again.outcome().error());
        assertEquals(Option.none(), again.popup());
    }

    @Test
    void readingThePullRequestsAgainForgetsTheFailure() {
        Model failed = Update.update(loaded(), new PullRequestsMsg.LoadFailed(refused("offline")))
                .model();

        Model loaded =
                Update.update(failed, new PullRequestsMsg.Loaded(List.empty())).model();

        assertEquals(Option.none(), loaded.gitHub().pullRequestsFailure());
    }

    @Test
    void aReadOfThePullRequestsIsKeptWhileItRunsAndNotStartedTwice() {
        Next reading = Update.update(loaded(), new PullRequestsMsg.Refresh());
        assertEquals(
                List.of(new LoadPullRequests(), new LoadBranchChecks()),
                reading.model().gitHub().reading());

        Next again = Update.update(reading.model(), new PullRequestsMsg.Refresh());
        assertEquals(List.empty(), again.cmds());
        assertEquals(
                List.of(new LoadPullRequests(), new LoadBranchChecks()),
                again.model().gitHub().reading());

        assertEquals(
                List.of(new LoadBranchChecks()),
                Update.update(reading.model(), new PullRequestsMsg.Loaded(List.empty()))
                        .model()
                        .gitHub()
                        .reading());
        assertEquals(
                List.of(new LoadBranchChecks()),
                Update.update(reading.model(), new PullRequestsMsg.LoadFailed(refused("offline")))
                        .model()
                        .gitHub()
                        .reading());
    }

    @Test
    void refreshingReadsThePullRequests() {
        assertEquals(
                List.of(new LoadPullRequests(), new LoadBranchChecks()),
                Update.update(loaded(), new PullRequestsMsg.Refresh()).cmds());
        assertInstanceOf(
                PullRequestsMsg.Loaded.class,
                new LoadPullRequests().run(new Env(new StubGitProvider() {}, StubGitHub.empty())));
    }

    @Test
    void aReadIsStartedAnywayWhenAChangeMayHaveBeenMissed() {
        Next reading = Update.update(loaded(), new PullRequestsMsg.Refresh());

        Next again = PullRequestsUpdate.loadAnyway(Next.of(reading.model()));

        assertEquals(List.of(new LoadPullRequests(), new LoadBranchChecks()), again.cmds());
        assertEquals(
                List.of(new LoadPullRequests(), new LoadBranchChecks(), new LoadPullRequests(), new LoadBranchChecks()),
                again.model().gitHub().reading());

        Model oneRead = Update.update(again.model(), new PullRequestsMsg.Loaded(List.empty()))
                .model();
        assertEquals(
                List.of(new LoadBranchChecks(), new LoadPullRequests(), new LoadBranchChecks()),
                oneRead.gitHub().reading());
    }
}
