package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Reading how the CI checks are doing on the branches on GitHub, and which branches show them.
 */
class BranchChecksUpdateTest {

    private static final GitHubChecks ON_ORIGIN = new GitHubChecks(
            HashSet.of("origin"),
            HashMap.of(new Branch.Name("feature"), Checks.FAILING, new Branch.Name("main"), Checks.PASSING));
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);

    private static RemoteBranch remote(String remote, String branch) {
        return new RemoteBranch(remote, new Branch.Name(branch));
    }

    private static Branch following(String name, RemoteBranch upstream) {
        return new Branch(
                new Branch.Name(name),
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(upstream, 1, 0)));
    }

    private static Model withChecks() {
        return Update.update(loaded(), new BranchChecksMsg.Loaded(ON_ORIGIN)).model();
    }

    @Test
    void theChecksAreReadOnTheRemotesThatPointAtTheGitHubRepository() {
        Msg msg = new LoadBranchChecks()
                .run(new Env(
                        new StubGitProvider() {
                            @Override
                            public Either<ProcessError, Map<String, String>> remoteUrls() {
                                return Either.right(HashMap.of(
                                        "origin",
                                        "git@github.com:me/repo.git",
                                        "upstream",
                                        "git@github.com:owner/repo.git",
                                        "mirror",
                                        "https://github.com/owner/repo"));
                            }
                        },
                        new StubGitHub() {
                            @Override
                            public Either<GhError, BranchChecks> branchChecks() {
                                return Either.right(new BranchChecks("owner/repo", HashMap.of("main", Checks.PASSING)));
                            }
                        }));

        assertEquals(
                new BranchChecksMsg.Loaded(new GitHubChecks(
                        HashSet.of("upstream", "mirror"), HashMap.of(new Branch.Name("main"), Checks.PASSING))),
                msg);
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
        Next reading = BranchChecksUpdate.load(Next.of(loaded()));
        assertEquals(List.of(new LoadBranchChecks()), reading.cmds());

        Next again = BranchChecksUpdate.load(Next.of(reading.model()));
        assertEquals(List.empty(), again.cmds());

        assertEquals(
                HashSet.empty(),
                Update.update(reading.model(), new BranchChecksMsg.Loaded(ON_ORIGIN))
                        .model()
                        .gitHub()
                        .reading());
        assertEquals(
                HashSet.empty(),
                Update.update(reading.model(), new BranchChecksMsg.LoadFailed(refused("offline")))
                        .model()
                        .gitHub()
                        .reading());
    }

    @Test
    void checksAskedForWhileARunningReadMayHaveMissedAChangeAreReadOnceThatOneIsDone() {
        Next reading = BranchChecksUpdate.load(Next.of(loaded()));

        Next asked = BranchChecksUpdate.loadAnyway(Next.of(reading.model()));
        assertEquals(List.empty(), asked.cmds());

        Next done = Update.update(asked.model(), new BranchChecksMsg.Loaded(ON_ORIGIN));
        assertEquals(List.of(new LoadBranchChecks()), done.cmds());
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
