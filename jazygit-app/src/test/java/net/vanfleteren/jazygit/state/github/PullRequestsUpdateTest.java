package net.vanfleteren.jazygit.state.github;

import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.state.load.LoadBranches;
import net.vanfleteren.jazygit.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

/**
 * Reading the open pull requests from GitHub.
 */
class PullRequestsUpdateTest {

    /**
     * The repository read, with the protected branches read too: they are read once, before the first pull requests.
     */
    private static Model loaded() {
        return TestModels.loaded().mapGitHub(gitHub -> gitHub.withProtectedBranches(Option.of(HashSet.empty())));
    }

    private static final PullRequest FEATURE_PR =
            new PullRequest(42, new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED);

    @Test
    void ofSeveralPullRequestsFromTheSameBranchTheNewestIsKeptWhateverTheOrder() {
        PullRequest older = new PullRequest(7, new GhBranch("feature"), false, Checks.NONE, Review.NONE);

        assertEquals(
                Option.of(FEATURE_PR),
                Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(older, FEATURE_PR)))
                        .model()
                        .gitHub()
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));
        assertEquals(
                Option.of(FEATURE_PR),
                Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR, older)))
                        .model()
                        .gitHub()
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));
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
        Next reading = TestModels.stepped(loaded(), new PullRequestsMsg.Refresh());
        assertEquals(
                HashSet.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                TestModels.runningCmds(reading.model()));

        Next again = Update.update(reading.model(), new PullRequestsMsg.Refresh());
        assertEquals(List.empty(), again.cmds());

        assertEquals(
                HashSet.of(new LoadBranchChecks()),
                TestModels.runningCmds(TestModels.done(
                                reading.model(),
                                new LoadRepositoryPullRequests(),
                                new PullRequestsMsg.Loaded(List.empty()))
                        .model()));
        assertEquals(
                HashSet.of(new LoadBranchChecks()),
                TestModels.runningCmds(TestModels.done(
                                reading.model(),
                                new LoadRepositoryPullRequests(),
                                new PullRequestsMsg.LoadFailed(refused("offline")))
                        .model()));
    }

    @Test
    void refreshingReadsThePullRequests() {
        assertEquals(
                List.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                Update.update(loaded(), new PullRequestsMsg.Refresh()).cmds());
        assertInstanceOf(
                PrOverviewMsg.RepositoryLoaded.class,
                new LoadRepositoryPullRequests().run(new Env(StubGit.of(), StubGitHub.empty())));
    }

    @Test
    void aReadAskedForWhileOneRunsAsAChangeMayHaveBeenMissedStartsOnceThatOneIsDone() {
        Next reading = TestModels.stepped(loaded(), new PullRequestsMsg.Refresh());

        // Asked for twice while the first runs, as after a push and then the creation of a pull request.
        Next asked = PullRequestsUpdate.loadAnyway(PullRequestsUpdate.loadAnyway(Next.of(reading.model())));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                HashSet.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                asked.model().gitHub().again());

        Next done = TestModels.done(
                asked.model(), new LoadRepositoryPullRequests(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)));

        assertEquals(List.of(new LoadRepositoryPullRequests()), done.cmds());
        assertEquals(
                HashSet.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                TestModels.runningCmds(done.model()));
        assertEquals(HashSet.of(new LoadBranchChecks()), done.model().gitHub().again());
        // What the first read read shows until the one started after the change is done.
        assertEquals(
                Option.of(FEATURE_PR),
                done.model().gitHub().pullRequests().flatMap(open -> open.get(new BranchName("feature"))));

        Next readAgain = TestModels.done(
                done.model(), new LoadRepositoryPullRequests(), new PullRequestsMsg.Loaded(List.empty()));

        assertEquals(List.empty(), readAgain.cmds());
        assertEquals(
                Option.of(io.vavr.collection.HashMap.empty()),
                readAgain.model().gitHub().pullRequests());
    }

    @Test
    void aReadThatFailsStillStartsTheOneAskedForMeanwhile() {
        Model asked = PullRequestsUpdate.loadAnyway(TestModels.stepped(loaded(), new PullRequestsMsg.Refresh()))
                .model();

        Next failed = TestModels.done(
                asked, new LoadRepositoryPullRequests(), new PullRequestsMsg.LoadFailed(refused("offline")));

        assertEquals(List.of(new LoadRepositoryPullRequests()), failed.cmds());
        assertEquals(
                HashSet.empty(), failed.model().gitHub().again().filter(LoadRepositoryPullRequests.class::isInstance));
    }

    @Test
    void aReadAskedForAnywayWhileNoneRunsStartsRightAway() {
        Next asked = PullRequestsUpdate.loadAnyway(Next.of(loaded()));

        assertEquals(List.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()), asked.cmds());
        assertEquals(HashSet.empty(), asked.model().gitHub().again());
    }

    @Test
    void theProtectedBranchesAreReadOnceWithThePullRequests() {
        Next first = Update.update(TestModels.loaded(), new PullRequestsMsg.Refresh());
        assertEquals(
                List.of(new LoadProtectedBranches(), new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                first.cmds());

        Model read = Update.update(TestModels.loaded(), new PullRequestsMsg.ProtectedLoaded(List.of("develop")))
                .model();
        assertEquals(
                Option.of(HashSet.of(new BranchName("develop"))), read.gitHub().protectedBranches());
        assertEquals(
                List.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                Update.update(read, new PullRequestsMsg.Refresh()).cmds());
    }

    @Test
    void protectedBranchesThatCannotBeReadAreReadAgainLater() {
        Model failed = Update.update(TestModels.loaded(), new PullRequestsMsg.ProtectedLoadFailed(refused("offline")))
                .model();

        assertEquals(Option.none(), failed.gitHub().protectedBranches());
    }

    @Test
    void theBranchesAreReadAgainWhenGitHubSaysAnotherBaseForOne() {
        PullRequest onA = new PullRequest(
                42,
                new GhBranch("feature"),
                new GhBranch("feat/a"),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.none(),
                Option.none());

        Next read = Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(onA)));
        assertEquals(
                List.of(new LoadBranches(GitHubBases.of(read.model().gitHub()))),
                read.cmds().filter(LoadBranches.class::isInstance));

        Next same = Update.update(read.model(), new PullRequestsMsg.Loaded(List.of(onA)));
        assertEquals(List.empty(), same.cmds().filter(LoadBranches.class::isInstance));
    }

    @Test
    void theBranchesAreReadAgainWhenTheProtectedBranchesAreRead() {
        Next read = Update.update(TestModels.loaded(), new PullRequestsMsg.ProtectedLoaded(List.of("develop")));

        assertEquals(
                List.of(new LoadBranches(GitHubBases.of(read.model().gitHub()))),
                read.cmds().filter(LoadBranches.class::isInstance));
    }
}
