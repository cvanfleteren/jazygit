package net.vanfleteren.jazygit.features.github.prs;

import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.checks.branches.LoadBranchChecks;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.LoadProtectedBranches;
import net.vanfleteren.jazygit.features.github.state.PullRequestsError;
import net.vanfleteren.jazygit.features.github.state.PullRequestsMsg;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;
import net.vanfleteren.jazygit.kernel.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

/**
 * Reading the open pull requests from GitHub, which the branches show (ADR 168).
 */
class PullRequestReadsTest {

    /**
     * The repository read, with the protected branches read too: they are read once, before the first pull requests.
     */
    private static Model loaded() {
        return TestModels.loaded()
                .mapPart(GitHubState.PART, gitHub -> gitHub.withProtectedBranches(Option.of(HashSet.empty())));
    }

    private static final PullRequest FEATURE_PR =
            new PullRequest(new PrNumber(42), new ForgeBranch("feature"), false, Checks.PASSING, Review.APPROVED);

    @Test
    void ofSeveralPullRequestsFromTheSameBranchTheNewestIsKeptWhateverTheOrder() {
        PullRequest older =
                new PullRequest(new PrNumber(7), new ForgeBranch("feature"), false, Checks.NONE, Review.NONE);

        assertEquals(
                Option.of(FEATURE_PR),
                Update.update(loaded(), new Read(List.of(older, FEATURE_PR)))
                        .model()
                        .part(GitHubState.PART)
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));
        assertEquals(
                Option.of(FEATURE_PR),
                Update.update(loaded(), new Read(List.of(FEATURE_PR, older)))
                        .model()
                        .part(GitHubState.PART)
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));
    }

    @Test
    void failingToReadThePullRequestsKeepsTheOnesShownWithoutAnError() {
        Model withPr = Update.update(loaded(), new Read(List.of(FEATURE_PR))).model();

        Model failed =
                Update.update(withPr, new Failed(refused("gh: not logged in"))).model();

        assertEquals(
                withPr.part(GitHubState.PART).pullRequests(),
                failed.part(GitHubState.PART).pullRequests());
        assertEquals(Option.none(), failed.outcome().error());
        assertEquals(Option.none(), failed.popup());
    }

    @Test
    void theAccountChosenForTheRepositoryNotBeingAvailableIsShownOnce() {
        Failure unavailable = new GitHubFailure(
                new GhError.TokenUnavailable(
                        new GhAccount("github.com", "alice-work"),
                        new GhError.CommandFailed(Option.of("no oauth token found"), 1)),
                List.empty());

        Model failed = Update.update(loaded(), new Failed(unavailable)).model();
        Model again = Update.update(failed.clearError().withoutPopup(ErrorPopup.class), new Failed(unavailable))
                .model();

        assertEquals(
                Option.of(new PullRequestsError.Unavailable(unavailable)),
                failed.outcome().error());
        assertEquals(Option.none(), again.outcome().error());
        assertEquals(Option.none(), again.popup());
    }

    @Test
    void readingThePullRequestsAgainForgetsTheFailure() {
        Model failed = Update.update(loaded(), new Failed(refused("offline"))).model();

        Model loaded = Update.update(failed, new Read(List.empty())).model();

        assertEquals(Option.none(), loaded.part(GitHubState.PART).pullRequestsFailure());
    }

    @Test
    void aReadOfThePullRequestsIsKeptWhileItRunsAndNotStartedTwice() {
        Next reading = TestModels.stepped(loaded(), new Refresh());
        assertEquals(
                HashSet.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                TestModels.runningCmds(reading.model()));

        Next again = Update.update(reading.model(), new Refresh());
        assertEquals(List.empty(), again.cmds());

        assertEquals(
                HashSet.of(new LoadBranchChecks()),
                TestModels.runningCmds(
                        TestModels.done(reading.model(), new LoadRepositoryPullRequests(), new Read(List.empty()))
                                .model()));
        assertEquals(
                HashSet.of(new LoadBranchChecks()),
                TestModels.runningCmds(TestModels.done(
                                reading.model(), new LoadRepositoryPullRequests(), new Failed(refused("offline")))
                        .model()));
    }

    @Test
    void refreshingReadsThePullRequests() {
        assertEquals(
                List.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                Update.update(loaded(), new Refresh()).cmds());
        assertInstanceOf(
                PrOverviewMsg.RepositoryLoaded.class,
                new LoadRepositoryPullRequests().run(new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of())));
    }

    @Test
    void aReadAskedForWhileOneRunsAsAChangeMayHaveBeenMissedStartsOnceThatOneIsDone() {
        Next reading = TestModels.stepped(loaded(), new Refresh());

        // Asked for twice while the first runs, as after a push and then the creation of a pull request.
        Next asked = PullRequestReads.loadAnyway(PullRequestReads.loadAnyway(Next.of(reading.model())));

        assertEquals(List.empty(), asked.cmds());
        assertEquals(
                HashSet.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                asked.model().part(GitHubState.PART).again());

        Next done = TestModels.done(asked.model(), new LoadRepositoryPullRequests(), new Read(List.of(FEATURE_PR)));

        assertEquals(List.of(new LoadRepositoryPullRequests()), done.cmds());
        assertEquals(
                HashSet.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                TestModels.runningCmds(done.model()));
        assertEquals(
                HashSet.of(new LoadBranchChecks()),
                done.model().part(GitHubState.PART).again());
        // What the first read read shows until the one started after the change is done.
        assertEquals(
                Option.of(FEATURE_PR),
                done.model()
                        .part(GitHubState.PART)
                        .pullRequests()
                        .flatMap(open -> open.get(new BranchName("feature"))));

        Next readAgain = TestModels.done(done.model(), new LoadRepositoryPullRequests(), new Read(List.empty()));

        assertEquals(List.empty(), readAgain.cmds());
        assertEquals(
                Option.of(io.vavr.collection.HashMap.empty()),
                readAgain.model().part(GitHubState.PART).pullRequests());
    }

    @Test
    void aReadThatFailsStillStartsTheOneAskedForMeanwhile() {
        Model asked = PullRequestReads.loadAnyway(TestModels.stepped(loaded(), new Refresh()))
                .model();

        Next failed = TestModels.done(asked, new LoadRepositoryPullRequests(), new Failed(refused("offline")));

        assertEquals(List.of(new LoadRepositoryPullRequests()), failed.cmds());
        assertEquals(
                HashSet.empty(),
                failed.model().part(GitHubState.PART).again().filter(LoadRepositoryPullRequests.class::isInstance));
    }

    @Test
    void aReadKeepsTheRepositoryOnGitHubWhichAFailedReadLeavesAsItWas() {
        // The templates of where a worktree starts out name it by its owner and name (ADR 134, ADR 168).
        Model read = PullRequestReads.loaded(loaded(), List.of(FEATURE_PR), new RepoSlug("octo/repo"))
                .model();
        Model failed = PullRequestReads.failed(read, refused("offline")).model();

        assertEquals(Option.none(), loaded().part(GitHubState.PART).repository());
        assertEquals(Option.of("octo/repo"), read.part(GitHubState.PART).repository());
        assertEquals(Option.of("octo/repo"), failed.part(GitHubState.PART).repository());
    }

    @Test
    void aReadAskedForAnywayWhileNoneRunsStartsRightAway() {
        Next asked = PullRequestReads.loadAnyway(Next.of(loaded()));

        assertEquals(List.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()), asked.cmds());
        assertEquals(HashSet.empty(), asked.model().part(GitHubState.PART).again());
    }

    @Test
    void theProtectedBranchesAreReadOnceWithThePullRequests() {
        Next first = Update.update(TestModels.loaded(), new Refresh());
        assertEquals(
                List.of(new LoadProtectedBranches(), new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                first.cmds());

        Model read = Update.update(
                        TestModels.loaded(), new PullRequestsMsg.ProtectedLoaded(List.of(new ForgeBranch("develop"))))
                .model();
        assertEquals(
                Option.of(HashSet.of(new BranchName("develop"))),
                read.part(GitHubState.PART).protectedBranches());
        assertEquals(
                List.of(new LoadRepositoryPullRequests(), new LoadBranchChecks()),
                Update.update(read, new Refresh()).cmds());
    }

    @Test
    void protectedBranchesThatCannotBeReadAreReadAgainLater() {
        Model failed = Update.update(TestModels.loaded(), new PullRequestsMsg.ProtectedLoadFailed(refused("offline")))
                .model();

        assertEquals(Option.none(), failed.part(GitHubState.PART).protectedBranches());
    }

    @Test
    void theBranchesAreReadAgainWhenGitHubSaysAnotherBaseForOne() {
        PullRequest onA = new PullRequest(
                new PrNumber(42),
                new ForgeBranch("feature"),
                Option.of(new ForgeBranch("feat/a")),
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.none(),
                Option.none());

        Next read = Update.update(loaded(), new Read(List.of(onA)));
        assertEquals(
                List.of(new LoadBranches(BranchBases.in(read.model()))),
                read.cmds().filter(LoadBranches.class::isInstance));

        Next same = Update.update(read.model(), new Read(List.of(onA)));
        assertEquals(List.empty(), same.cmds().filter(LoadBranches.class::isInstance));
    }

    @Test
    void theBranchesAreReadAgainWhenTheProtectedBranchesAreRead() {
        Next read = Update.update(
                TestModels.loaded(), new PullRequestsMsg.ProtectedLoaded(List.of(new ForgeBranch("develop"))));

        assertEquals(
                List.of(new LoadBranches(BranchBases.in(read.model()))),
                read.cmds().filter(LoadBranches.class::isInstance));
    }

    /**
     * The open pull requests {@code pullRequests} were read, as the screen's update hands them to the branches.
     */
    private record Read(List<PullRequest> pullRequests) implements Msg {

        @Override
        public Next apply(Model model) {
            return PullRequestReads.loaded(model, pullRequests, new RepoSlug("owner/repo"));
        }
    }

    /**
     * The open pull requests could not be read for {@code failure}.
     */
    private record Failed(Failure failure) implements Msg {

        @Override
        public Next apply(Model model) {
            return PullRequestReads.failed(model, failure);
        }
    }

    /**
     * The pull requests are asked to be read again.
     */
    private record Refresh() implements Msg {

        @Override
        public Next apply(Model model) {
            return PullRequestReads.load(Next.of(model));
        }
    }
}
