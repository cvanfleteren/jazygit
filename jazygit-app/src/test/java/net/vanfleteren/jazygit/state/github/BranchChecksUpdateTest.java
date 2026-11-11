package net.vanfleteren.jazygit.state.github;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubRemotes;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.LoadBranches;
import org.junit.jupiter.api.Test;

/**
 * Reading how the CI checks are doing on the branches on GitHub, and which branches show them.
 */
class BranchChecksUpdateTest {

    private static final GitHubChecks ON_ORIGIN = new GitHubChecks(
            HashSet.of(new RemoteName("origin")),
            HashMap.of(new BranchName("feature"), Checks.FAILING, new BranchName("main"), Checks.PASSING));
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED);

    private static RemoteBranch remote(String remote, String branch) {
        return new RemoteBranch(remote, new BranchName(branch));
    }

    private static Branch following(String name, RemoteBranch upstream) {
        return Fixtures.branch(
                new BranchName(name),
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(upstream), 1, 0)));
    }

    /**
     * The reads {@code next} starts, apart from reading the branches again as the remotes on GitHub became known.
     */
    private static List<Cmd> reads(Next next) {
        return next.cmds().filter(cmd -> !(cmd instanceof LoadBranches));
    }

    private static Model withChecks() {
        return Update.update(loaded(), new BranchChecksMsg.Loaded(ON_ORIGIN)).model();
    }

    @Test
    void theBranchesAreReadAgainWhenTheRemotesOnGitHubAreRead() {
        Next read = Update.update(loaded(), new BranchChecksMsg.Loaded(ON_ORIGIN));
        assertEquals(
                List.of(new LoadBranches(GitHubBases.of(read.model().gitHub()))),
                read.cmds().filter(LoadBranches.class::isInstance));

        Next same = Update.update(read.model(), new BranchChecksMsg.Loaded(ON_ORIGIN));
        assertEquals(List.empty(), same.cmds().filter(LoadBranches.class::isInstance));
    }

    @Test
    void theChecksAreReadOnTheRemotesThatPointAtTheGitHubRepository() {
        Msg msg = new LoadBranchChecks()
                .run(new Env(
                        StubGit.of().withRemotes(new StubRemotes() {
                            @Override
                            public Either<GitError, Map<RemoteName, String>> urls() {
                                return Either.right(HashMap.of(
                                                "origin",
                                                "git@github.com:me/repo.git",
                                                "upstream",
                                                "git@github.com:owner/repo.git",
                                                "mirror",
                                                "https://github.com/owner/repo")
                                        .mapKeys(RemoteName::new));
                            }
                        }),
                        new StubGitHub() {
                            @Override
                            public Either<GhError, BranchChecks> branchChecks() {
                                return Either.right(new BranchChecks(
                                        "owner/repo", HashMap.of(new GhBranch("main"), Checks.PASSING)));
                            }
                        }));

        assertEquals(
                new BranchChecksMsg.Loaded(new GitHubChecks(
                        HashSet.of("upstream", "mirror").map(RemoteName::new),
                        HashMap.of(new BranchName("main"), Checks.PASSING))),
                msg);
    }

    private static Branch branch(String name, boolean current, boolean isDefault, String upstream) {
        return Fixtures.branch(
                new BranchName(name),
                current,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                isDefault,
                Option.of(new Upstream(new BranchRef.Remote(remote("origin", upstream)), 0, 0)));
    }

    private static GitHubChecks pending(String... branches) {
        return new GitHubChecks(
                HashSet.of(new RemoteName("origin")),
                List.of(branches)
                        .toMap(branch -> new BranchName(branch), branch -> Checks.PENDING)
                        .put(new BranchName("done"), Checks.PASSING));
    }

    private static PullRequestDetails.Check running(String name) {
        return new PullRequestDetails.Check(
                name,
                Checks.PENDING,
                Option.of(new CheckRun(
                        name.length(),
                        "Build",
                        "",
                        Option.of(Instant.EPOCH.plusSeconds(10)),
                        Option.none(),
                        1,
                        Instant.EPOCH,
                        1,
                        "push")));
    }

    /**
     * A check of workflow {@code Build} that waits to start, of attempt {@code attempt} of a run created at EPOCH.
     */
    private static PullRequestDetails.Check queued(String name, int attempt) {
        return new PullRequestDetails.Check(
                name,
                Checks.PENDING,
                Option.of(new CheckRun(
                        name.length(), "Build", "", Option.none(), Option.none(), 1, Instant.EPOCH, attempt, "push")));
    }

    /**
     * {@code model} with the checks on the pending branch {@code feature} read one by one as {@code checks}.
     */
    private static Model readOneByOne(List<PullRequestDetails.Check> checks) {
        return Update.update(
                        Update.update(loaded(), new BranchChecksMsg.Loaded(pending("feature")))
                                .model(),
                        new BranchChecksMsg.RunsLoaded(
                                new PendingChecks("owner/repo", HashMap.of(new BranchName("feature"), checks))))
                .model();
    }

    @Test
    void aPendingBranchWaitsWhileNoneOfItsChecksThatAreNotDoneRuns() {
        PullRequestDetails.Check passed = new PullRequestDetails.Check("lint", Checks.PASSING);

        assertEquals(
                Option.of(new Waiting(Option.of(Instant.EPOCH))),
                readOneByOne(List.of(passed, queued("verify", 1))).gitHub().waiting(remote("origin", "feature")));
        assertEquals(
                Option.none(),
                readOneByOne(List.of(queued("verify", 1), running("package")))
                        .gitHub()
                        .waiting(remote("origin", "feature")));
        // GitHub does not say when a re-run started.
        assertEquals(
                Option.of(new Waiting(Option.none())),
                readOneByOne(List.of(queued("verify", 2))).gitHub().waiting(remote("origin", "feature")));
        assertEquals(
                Option.none(),
                readOneByOne(List.of(queued("verify", 1))).gitHub().waiting(remote("origin", "done")));
    }

    @Test
    void theBranchesWhoseChecksArePendingAreReadOneByOneTheCheckedOutAndTheDefaultOneFirst() {
        Model model = TestModels.localBranchesRead(
                        loaded(), List.of(branch("main", false, true, "trunk"), branch("mine", true, false, "zeta")))
                .model();

        Next next = Update.update(model, new BranchChecksMsg.Loaded(pending("alpha", "trunk", "zeta")));

        assertEquals(
                List.of(new LoadBranchCheckRuns(
                        List.of("zeta", "trunk", "alpha").map(BranchName::new))),
                reads(next));
    }

    @Test
    void theChecksOfPendingBranchesAreNotReadTwiceAtOnceSoAnOlderReadCannotReplaceANewerOne() {
        Next first = TestModels.stepped(loaded(), new BranchChecksMsg.Loaded(pending("feature")));
        assertEquals(1, reads(first).size());

        Next second = Update.update(first.model(), new BranchChecksMsg.Loaded(pending("feature")));
        assertEquals(List.empty(), second.cmds());

        Model done = TestModels.done(
                        second.model(),
                        first.cmds().head(),
                        new BranchChecksMsg.RunsLoaded(new PendingChecks(
                                "owner/repo", HashMap.of(new BranchName("feature"), List.of(running("verify"))))))
                .model();
        assertEquals(
                1,
                Update.update(done, new BranchChecksMsg.Loaded(pending("feature")))
                        .cmds()
                        .size());
        Model failed = TestModels.done(
                        second.model(), first.cmds().head(), new BranchChecksMsg.RunsFailed(refused("offline")))
                .model();
        assertEquals(
                1,
                Update.update(failed, new BranchChecksMsg.Loaded(pending("feature")))
                        .cmds()
                        .size());
    }

    @Test
    void atMostTenPendingBranchesAreReadAndNoneWhenNoneIsPending() {
        Next many = Update.update(
                loaded(),
                new BranchChecksMsg.Loaded(pending(
                        List.range(0, 12).map(i -> "b" + (char) ('a' + i)).toJavaArray(String[]::new))));

        assertEquals(
                CheckTimings.PENDING_BRANCHES,
                ((LoadBranchCheckRuns) reads(many).last()).branches().size());
        assertEquals(List.empty(), reads(Update.update(loaded(), new BranchChecksMsg.Loaded(ON_ORIGIN))));
    }

    @Test
    void aPendingBranchReadOneByOneHasAnEtaAndTeachesHowLongItsChecksTake() {
        Model model = Update.update(loaded(), new BranchChecksMsg.Loaded(pending("feature")))
                .model();

        Next read = Update.update(
                model,
                new BranchChecksMsg.RunsLoaded(new PendingChecks(
                        "owner/repo", HashMap.of(new BranchName("feature"), List.of(running("verify"))))));

        assertEquals(Option.of(new Eta.Unknown()), read.model().gitHub().eta(remote("origin", "feature")));
        assertEquals(Option.none(), read.model().gitHub().eta(remote("origin", "done")));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        "owner/repo", List.empty(), read.model().now(), true)),
                read.cmds());
    }

    @Test
    void aPendingBranchWaitsForTheChecksThatDoNotShowYet() {
        // package runs 60s; acceptance waits for it, so GitHub makes it once package is done, 254s into its run.
        List<CheckSample> samples = List.range(0, 3)
                .flatMap(i -> List.of(
                        new CheckSample(
                                "Build/package",
                                i,
                                Duration.ofSeconds(60),
                                Duration.ofSeconds(60),
                                "push",
                                Instant.EPOCH.plusSeconds(i)),
                        new CheckSample(
                                "Build/acceptance",
                                10 + i,
                                Duration.ofSeconds(190),
                                Duration.ofSeconds(254),
                                "push",
                                Instant.EPOCH.plusSeconds(i))));
        CheckTimes times = CheckTimes.of("owner/repo", CheckHistory.EMPTY.with(samples, Instant.EPOCH), Option.none());

        Model model =
                readOneByOne(List.of(running("package"))).mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(times)));

        assertEquals(
                Option.of(new Eta.Expected(Instant.EPOCH.plusSeconds(254))),
                model.gitHub().eta(remote("origin", "feature")));
    }

    @Test
    void aBranchNoLongerPendingNoLongerHasAnEta() {
        Model read = Update.update(
                        Update.update(loaded(), new BranchChecksMsg.Loaded(pending("feature")))
                                .model(),
                        new BranchChecksMsg.RunsLoaded(new PendingChecks(
                                "owner/repo", HashMap.of(new BranchName("feature"), List.of(running("verify"))))))
                .model();

        Model done = Update.update(read, new BranchChecksMsg.Loaded(pending())).model();

        assertEquals(Option.none(), done.gitHub().eta(remote("origin", "feature")));
        assertEquals(HashMap.empty(), done.gitHub().pendingChecks().get().byBranch());
    }

    @Test
    void failingToReadTheChecksKeepsTheOnesShownWithoutAnError() {
        Model failed = Update.update(withChecks(), new BranchChecksMsg.LoadFailed(refused("offline")))
                .model();

        assertEquals(Option.of(ON_ORIGIN), failed.gitHub().branchChecks());
        assertEquals(Option.none(), failed.outcome().error());
    }

    @Test
    void aReadOfTheChecksIsKeptWhileItRunsAndNotStartedTwice() {
        Next reading = TestModels.started(BranchChecksUpdate.load(Next.of(loaded())));
        assertEquals(List.of(new LoadBranchChecks()), reading.cmds());

        Next again = BranchChecksUpdate.load(Next.of(reading.model()));
        assertEquals(List.empty(), again.cmds());

        assertFalse(TestModels.done(reading.model(), new LoadBranchChecks(), new BranchChecksMsg.Loaded(ON_ORIGIN))
                .model()
                .jobs()
                .runs(new LoadBranchChecks()));
        assertFalse(TestModels.done(
                        reading.model(), new LoadBranchChecks(), new BranchChecksMsg.LoadFailed(refused("offline")))
                .model()
                .jobs()
                .runs(new LoadBranchChecks()));
    }

    @Test
    void checksAskedForWhileARunningReadMayHaveMissedAChangeAreReadOnceThatOneIsDone() {
        Next reading = TestModels.started(BranchChecksUpdate.load(Next.of(loaded())));

        Next asked = BranchChecksUpdate.loadAnyway(Next.of(reading.model()));
        assertEquals(List.empty(), asked.cmds());

        Next done = TestModels.done(asked.model(), new LoadBranchChecks(), new BranchChecksMsg.Loaded(ON_ORIGIN));
        assertEquals(List.of(new LoadBranchChecks()), reads(done));
        assertEquals(Option.of(ON_ORIGIN), done.model().gitHub().branchChecks());

        assertEquals(
                List.empty(),
                Update.update(done.model(), new BranchChecksMsg.Loaded(ON_ORIGIN))
                        .cmds());
    }

    @Test
    void aBranchOnGitHubShowsItsChecksAndALocalBranchThoseOfItsUpstream() {
        Model model = withChecks();

        assertEquals(Option.of(Checks.FAILING), model.gitHub().checks(remote("origin", "feature")));
        assertEquals(Option.of(Checks.FAILING), model.gitHub().checks(following("mine", remote("origin", "feature"))));
        assertEquals(Option.none(), model.gitHub().checks(remote("fork", "feature")));
        assertEquals(Option.none(), model.gitHub().checks(following("feature", remote("fork", "feature"))));
        assertEquals(Option.none(), model.gitHub().checks(remote("origin", "unknown")));
    }

    @Test
    void onlyABranchOnGitHubShowsItsPullRequest() {
        Model model = Update.update(withChecks(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(Option.of(FEATURE_PR), model.gitHub().pullRequest(remote("origin", "feature")));
        assertEquals(Option.none(), model.gitHub().pullRequest(remote("fork", "feature")));
        assertEquals(
                Option.of(FEATURE_PR), model.gitHub().pullRequest(following("feature", remote("origin", "feature"))));
        assertEquals(Option.none(), model.gitHub().pullRequest(following("feature", remote("fork", "feature"))));
    }

    @Test
    void untilTheRemotesOnGitHubAreKnownALocalBranchShowsThePullRequestByName() {
        Model model = Update.update(loaded(), new PullRequestsMsg.Loaded(List.of(FEATURE_PR)))
                .model();

        assertEquals(
                Option.of(FEATURE_PR), model.gitHub().pullRequest(following("feature", remote("fork", "feature"))));
        assertEquals(Option.none(), model.gitHub().pullRequest(remote("origin", "feature")));
    }
}
